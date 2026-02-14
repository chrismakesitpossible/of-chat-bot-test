package com.ofchatbot.controller;

import com.ofchatbot.dto.OnlyFansWebhookPayload;
import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhook/onlyfans")
@RequiredArgsConstructor
@Slf4j
public class OnlyFansWebhookController {

    private final OnlyFansChatbotService onlyFansChatbotService;
    private final FanService fanService;
    private final ConversationService conversationService;
    private final ScriptEngineService scriptEngineService;
    private final ErrorLogService errorLogService;
    private final CreatorService creatorService;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleOnlyFansWebhook(@RequestBody OnlyFansWebhookPayload webhook) {
        try {
            String event = webhook.getEvent();
            log.info("Received OnlyFans webhook event: {}", event);

            new Thread(() -> {
                try {
                    switch (event) {
                        case "subscriptions.new":
                            onlyFansChatbotService.processNewSubscription(webhook);
                            break;

                        case "messages.received":
                            onlyFansChatbotService.processIncomingMessage(webhook);
                            break;

                        case "transactions.new":
                            handleTransactionNew(webhook);
                            break;

                        case "messages.ppv.unlocked":
                            handlePpvUnlocked(webhook);
                            break;

                        case "tips.received":
                            handleTipReceived(webhook);
                            break;

                        case "subscriptions.renewed":
                            handleSubscriptionRenewed(webhook);
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
                        "Event: " + event
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

    private void handleTransactionNew(OnlyFansWebhookPayload webhook) {
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

    private void handlePpvUnlocked(OnlyFansWebhookPayload webhook) {
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

                Double purchaseAmount = totalSpending - state.getTotalSpent();
                if (purchaseAmount > 0) {
                    scriptEngineService.recordPurchase(state, purchaseAmount);
                }

                log.info("Recorded PPV unlock for fan: {} - Total spending: ${}", userId, totalSpending);
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

    private void handleTipReceived(OnlyFansWebhookPayload webhook) {
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

                Double tipAmount = totalSpending - state.getTotalSpent();
                if (tipAmount > 0) {
                    scriptEngineService.recordPurchase(state, tipAmount);
                }

                log.info("Recorded tip for fan: {} - Total spending: ${}", userId, totalSpending);
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

    private void handleSubscriptionRenewed(OnlyFansWebhookPayload webhook) {
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
}
