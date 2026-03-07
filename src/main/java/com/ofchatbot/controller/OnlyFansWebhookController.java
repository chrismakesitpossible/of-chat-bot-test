package com.ofchatbot.controller;

import com.ofchatbot.dto.OnlyFansWebhookPayload;
import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.service.*;
import com.ofchatbot.exception.SendToSelfException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhook/onlyfans")
@RequiredArgsConstructor
@Slf4j
public class OnlyFansWebhookController {

    private final OnlyFansChatbotService onlyFansChatbotService;
    private final MessageBatchingService messageBatchingService;
    private final FanService fanService;
    private final ConversationService conversationService;
    private final ScriptEngineService scriptEngineService;
    private final ErrorLogService errorLogService;
    private final CreatorService creatorService;
    private final PPVPurchaseService ppvPurchaseService;
    private final OnlyFansApiService onlyFansApiService;
    private final AnthropicService anthropicService;
    private final MessageService messageService;
    private final PPVLadderService ppvLadderService;
    private final CustomRequestService customRequestService;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleOnlyFansWebhook(@RequestBody OnlyFansWebhookPayload webhook) {
        try {
            String event = webhook.getEvent();
            String accountId = webhook.getAccount_id();
            log.info("Received OnlyFans webhook event: {} for account: {}", event, accountId);

            if (accountId == null || accountId.isEmpty()) {
                log.error("Webhook missing account_id");
                return ResponseEntity.badRequest().body(Map.of("error", "Missing account_id"));
            }

            Creator creator = creatorService.getOrCreateCreator(accountId);

            new Thread(() -> {
                try {
                    switch (event) {
                        case "subscriptions.new":
                            onlyFansChatbotService.processNewSubscription(webhook, creator);
                            break;

                        case "messages.received":
                            String fanId = webhook.getPayload().getFromUser().getId().toString();
                            messageBatchingService.handleIncomingMessage(fanId, () -> {
                                onlyFansChatbotService.processIncomingMessage(webhook, creator);
                            });
                            break;

                        case "transactions.new":
                            handleTransactionNew(webhook, creator);
                            break;

                        case "messages.ppv.unlocked":
                            handlePpvUnlocked(webhook, creator);
                            break;

                        case "tips.received":
                            handleTipReceived(webhook, creator);
                            break;

                        case "subscriptions.renewed":
                            handleSubscriptionRenewed(webhook, creator);
                            break;

                        default:
                            log.info("Unhandled webhook event: {}", event);
                    }
                } catch (Exception e) {
                    log.error("Error in async webhook processing", e);
                    errorLogService.logError(
                        "ASYNC_WEBHOOK_PROCESSING_FAILED",
                        "Failed in async webhook processing",
                        e,
                        "Event: " + event + ", Account: " + accountId
                    );
                }
            }).start();

            return ResponseEntity.ok(Map.of("status", "received"));

        } catch (Exception e) {
            log.error("Error processing OnlyFans webhook", e);
            errorLogService.logError(
                "ONLYFANS_WEBHOOK_PROCESSING_FAILED",
                "Failed to process OnlyFans webhook",
                e,
                "Event: " + webhook.getEvent()
            );
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process webhook"));
        }
    }

    private void handleTransactionNew(OnlyFansWebhookPayload webhook, Creator creator) {
        try {
            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            Double amount = payload.getFanData().getSpending().getTotal();
            
            Long userId = extractUserIdFromTransaction(payload);
            if (userId == null) {
                log.warn("Could not extract user ID from transaction webhook");
                return;
            }

            String onlyfansUserId = String.valueOf(userId);
            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(onlyfansUserId);

            if (fanOpt.isPresent()) {
                Fan fan = fanOpt.get();
                fan.setTotalSpending(amount);
                fanService.saveFan(fan);

                Conversation conversation = conversationService.getOrCreateConversation(fan);
                ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);

                Double transactionAmount = payload.getFanData().getSpending().getTotal() - state.getTotalSpent();
                if (transactionAmount > 0) {
                    scriptEngineService.recordPurchase(state, transactionAmount);
                }

                log.info("Recorded transaction for fan: {} - Amount: ${}", onlyfansUserId, transactionAmount);
            }

        } catch (Exception e) {
            log.error("Error handling transaction.new webhook", e);
            errorLogService.logError(
                "TRANSACTION_WEBHOOK_FAILED",
                "Failed to process transaction webhook",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }

    private void handlePpvUnlocked(OnlyFansWebhookPayload webhook, Creator creator) {
        try {
            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            String userId = payload.getUser_id();
            Double totalSpending = payload.getFanData() != null && payload.getFanData().getSpending() != null 
                ? payload.getFanData().getSpending().getTotal() 
                : null;
            
            Double ppvAmount = payload.getAmount();

            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(userId);

            if (fanOpt.isPresent()) {
                Fan fan = fanOpt.get();
                
                if (totalSpending != null) {
                    fan.setTotalSpending(totalSpending);
                    fanService.saveFan(fan);
                }

                Conversation conversation = conversationService.getOrCreateConversation(fan);
                ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);

                Double purchaseAmount = ppvAmount != null ? ppvAmount : 
                    (totalSpending != null && state.getTotalSpent() != null ? totalSpending - state.getTotalSpent() : null);
                
                if (purchaseAmount != null && purchaseAmount > 0) {
                    scriptEngineService.recordPurchase(state, purchaseAmount);
                    ppvPurchaseService.recordPPVPurchase(fan.getId(), purchaseAmount, fan.getOnlyfansChatId(), creator);
                    if (!Boolean.TRUE.equals(fan.getPassedWalletTest())) {
                        fan.setPassedWalletTest(true);
                        fanService.saveFan(fan);
                    }
                    sendPPVPurchaseThankYou(fan, state, purchaseAmount, creator);
                    ppvLadderService.handlePostPurchaseLadder(fan, state, purchaseAmount, userId);
                }

                log.info("Recorded PPV unlock for fan: {} - Amount: ${}", userId, purchaseAmount);
            }

        } catch (Exception e) {
            log.error("Error handling messages.ppv.unlocked webhook", e);
            errorLogService.logError(
                "PPV_UNLOCK_WEBHOOK_FAILED",
                "Failed to process PPV unlock webhook",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }

    private void handleTipReceived(OnlyFansWebhookPayload webhook, Creator creator) {
        try {
            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            String userId = payload.getUser_id();
            Double totalSpending = payload.getFanData() != null && payload.getFanData().getSpending() != null 
                ? payload.getFanData().getSpending().getTotal() 
                : null;
            
            Double tipAmount = payload.getAmount();

            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(userId);

            if (fanOpt.isPresent()) {
                Fan fan = fanOpt.get();
                
                if (totalSpending != null) {
                    fan.setTotalSpending(totalSpending);
                    fanService.saveFan(fan);
                }

                Conversation conversation = conversationService.getOrCreateConversation(fan);
                ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);

                Double recordedTipAmount = tipAmount != null ? tipAmount : 
                    (totalSpending != null && state.getTotalSpent() != null ? totalSpending - state.getTotalSpent() : null);
                
                if (recordedTipAmount != null && recordedTipAmount > 0) {
                    scriptEngineService.recordPurchase(state, recordedTipAmount);
                    
                    // If fan has a quoted custom and this tip is 50%+ advance, apply it and send "coming soon" instead of generic thank you.
                    if (!customRequestService.tryApplyTipToCustomAdvance(fan, recordedTipAmount)) {
                        sendTipThankYou(fan, state, recordedTipAmount, creator);
                    }
                }

                log.info("Recorded tip for fan: {} - Amount: ${}", userId, recordedTipAmount);
            }

        } catch (Exception e) {
            log.error("Error handling tips.received webhook", e);
            errorLogService.logError(
                "TIP_WEBHOOK_FAILED",
                "Failed to process tip webhook",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }

    private void handleSubscriptionRenewed(OnlyFansWebhookPayload webhook, Creator creator) {
        try {
            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            String userId = payload.getUser_id();
            Double totalSpending = payload.getFanData().getSpending().getTotal();

            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(userId);

            if (fanOpt.isPresent()) {
                Fan fan = fanOpt.get();
                fan.setTotalSpending(totalSpending);
                fanService.saveFan(fan);

                Conversation conversation = conversationService.getOrCreateConversation(fan);
                ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);

                state.setTotalSpent(totalSpending);
                scriptEngineService.updateConversationState(state, state.getCurrentState(), state.getLastScriptCategory());

                log.info("Updated spending for renewed subscription - Fan: {} - Total: ${}", userId, totalSpending);
            }

        } catch (Exception e) {
            log.error("Error handling subscriptions.renewed webhook", e);
            errorLogService.logError(
                "SUBSCRIPTION_RENEWED_WEBHOOK_FAILED",
                "Failed to process subscription renewed webhook",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }

    private Long extractUserIdFromTransaction(OnlyFansWebhookPayload.PayloadData payload) {
        String description = payload.getDescription();
        if (description != null && description.contains("href=\"https://onlyfans.com/")) {
            int startIdx = description.indexOf("href=\"https://onlyfans.com/") + 27;
            int endIdx = description.indexOf("\"", startIdx);
            if (endIdx > startIdx) {
                String username = description.substring(startIdx, endIdx);
                Optional<Fan> fanOpt = fanService.findByOnlyfansUsername(username);
                if (fanOpt.isPresent()) {
                    return Long.parseLong(fanOpt.get().getOnlyfansUserId());
                }
            }
        }
        return null;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    private void sendPPVPurchaseThankYou(Fan fan, ConversationState state, Double purchaseAmount, Creator creator) {
        try {
            String systemPrompt = buildPPVThankYouPrompt(state, purchaseAmount);
            String userPrompt = "Generate a thank you message for the PPV purchase.";

            String thankYouMessage = anthropicService.generatePPVThankYouMessage(systemPrompt, userPrompt, fan);

            onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), thankYouMessage, null, creator.getCreatorId());

            messageService.saveMessage(
                fan.getCreatorId(),
                fan.getOnlyfansUserId(),
                "bot",
                thankYouMessage,
                java.time.LocalDateTime.now().toString(),
                "onlyfans"
            );

            log.info("Sent PPV purchase thank you to fan {} for ${}", fan.getId(), purchaseAmount);

        } catch (SendToSelfException e) {
            log.info("Skipped PPV thank you for chat {} (cannot send to self, e.g. creator unlocked own PPV)", fan.getOnlyfansChatId());
        } catch (Exception e) {
            log.error("Error sending PPV purchase thank you", e);
            errorLogService.logError(
                "PPV_THANK_YOU_FAILED",
                "Failed to send PPV purchase thank you",
                e,
                "Fan ID: " + fan.getId() + ", Amount: $" + purchaseAmount
            );
        }
    }

    private String buildPPVThankYouPrompt(ConversationState state, Double purchaseAmount) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator who just received a PPV purchase from a fan.
            
            CONTEXT:
            - Purchase amount: $%.2f
            - Current conversation phase: %s
            - Intensity level: %d/10
            - The fan just unlocked your exclusive content
            
            TASK:
            Generate a genuine, appreciative thank you message that makes them feel special.
            
            CRITICAL RULES:
            - Be genuinely grateful and excited
            - Make them feel like they made a great choice
            - Keep it SHORT (1-2 sentences)
            - Match the current conversation vibe and intensity
            - Use 1-2 emojis that feel natural
            - If intensity is high (7+), be more flirty and suggestive
            - Hint that you hope they enjoy the content
            - Make them feel valued and special
            
            Generate ONLY the thank you message, nothing else.
            """, purchaseAmount, phase, intensity);
    }

    private void sendTipThankYou(Fan fan, ConversationState state, Double tipAmount, Creator creator) {
        try {
            String systemPrompt = buildTipThankYouPrompt(state, tipAmount);
            String userPrompt = "Generate a thank you message for the tip.";

            String thankYouMessage = anthropicService.generatePPVThankYouMessage(systemPrompt, userPrompt, fan);

            onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), thankYouMessage, null, creator.getCreatorId());

            messageService.saveMessage(
                fan.getCreatorId(),
                fan.getOnlyfansUserId(),
                "bot",
                thankYouMessage,
                java.time.LocalDateTime.now().toString(),
                "onlyfans"
            );

            log.info("Sent tip thank you to fan {} for ${}", fan.getId(), tipAmount);

        } catch (SendToSelfException e) {
            log.info("Skipped tip thank you for chat {} (cannot send to self)", fan.getOnlyfansChatId());
        } catch (Exception e) {
            log.error("Error sending tip thank you", e);
            errorLogService.logError(
                "TIP_THANK_YOU_FAILED",
                "Failed to send tip thank you",
                e,
                "Fan ID: " + fan.getId() + ", Amount: $" + tipAmount
            );
        }
    }

    private String buildTipThankYouPrompt(ConversationState state, Double tipAmount) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator who just received a tip from a fan.
            
            CONTEXT:
            - Tip amount: $%.2f
            - Current conversation phase: %s
            - Intensity level: %d/10
            - The fan just sent you a generous tip
            
            TASK:
            Generate a genuine, appreciative thank you message that makes them feel amazing.
            
            CRITICAL RULES:
            - Be genuinely grateful and excited
            - Make them feel generous and appreciated
            - Keep it SHORT (1-2 sentences)
            - Match the current conversation vibe and intensity
            - Use 1-2 emojis that feel natural
            - If intensity is high (7+), be more flirty and suggestive
            - Make them feel special and valued
            - Show excitement about the tip
        
            Generate ONLY the thank you message, nothing else.
            """, tipAmount, phase, intensity);
    }
}
