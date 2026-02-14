package com.ofchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.dto.OnlyFansWebhookPayload;
import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyFansChatbotService {

    private final FanService fanService;
    private final MessageService messageService;
    private final OnlyFansApiService onlyFansApiService;
    private final ErrorLogService errorLogService;
    private final AnthropicService anthropicService;
    private final ScriptEngineService scriptEngineService;
    private final ConversationService conversationService;
    private final CreatorService creatorService;
    private final ScriptAnalyticsService scriptAnalyticsService;

    public void processNewSubscription(OnlyFansWebhookPayload webhook) {
        try {
            log.info("Processing new OnlyFans subscription");

            String accountId = webhook.getAccount_id();
            Creator creator = creatorService.getOrCreateCreator(accountId);

            OnlyFansWebhookPayload.PayloadData.User user = webhook.getPayload().getUser();
            String onlyfansUserId = String.valueOf(user.getId());
            String onlyfansUsername = user.getUsername();
            String chatId = onlyfansUserId;

            Optional<Fan> existingFan = fanService.findByOnlyfansUserId(onlyfansUserId);

            if (existingFan.isEmpty()) {
                Fan newFan = new Fan();
                newFan.setCreatorId(creator.getCreatorId());
                newFan.setContactId(onlyfansUserId);
                newFan.setOnlyfansUserId(onlyfansUserId);
                newFan.setOnlyfansUsername(onlyfansUsername);
                newFan.setOnlyfansChatId(chatId);
                newFan.setMessageCount(0);
                newFan.setState("OPENING");
                newFan.setLastIntent("unknown");
                newFan.setTotalSpending(0.0);
                newFan.setLastUpdated(LocalDateTime.now());
                newFan.setCreatedAt(LocalDateTime.now());

                newFan = fanService.saveFan(newFan);
                log.info("Created new OnlyFans fan: {}", onlyfansUsername);

                Conversation conversation = conversationService.getOrCreateConversation(newFan);
                ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, newFan);

                String welcomeTemplate = scriptEngineService.getScriptTemplate("WELCOME", state);
                Map<String, String> frameworkGuidance = scriptEngineService.getFrameworkGuidance(state);

                scriptAnalyticsService.trackScriptUsage(creator.getCreatorId(), "WELCOME", state.getCurrentState());

                String welcomeMessage = anthropicService.generateScriptBasedResponse(
                    "New subscriber",
                    "",
                    newFan,
                    state,
                    welcomeTemplate,
                    frameworkGuidance
                );

                onlyFansApiService.sendMessage(chatId, welcomeMessage);

                messageService.saveMessage(
                    creator.getCreatorId(),
                    onlyfansUserId,
                    "bot",
                    welcomeMessage,
                    LocalDateTime.now().toString(),
                    "onlyfans"
                );

                scriptEngineService.updateConversationState(state, "CASUAL", "WELCOME");
            } else {
                log.info("Fan already exists: {}", onlyfansUsername);
            }

        } catch (Exception e) {
            log.error("Error processing new subscription", e);
            errorLogService.logError(
                "ONLYFANS_SUBSCRIPTION_PROCESSING_FAILED",
                "Failed to process new subscription",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }


    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").trim();
    }

    public void processIncomingMessage(OnlyFansWebhookPayload webhook) {
        try {
            log.info("Processing incoming OnlyFans message");

            String accountId = webhook.getAccount_id();
            Creator creator = creatorService.getOrCreateCreator(accountId);

            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            OnlyFansWebhookPayload.PayloadData.FromUser fromUser = payload.getFromUser();

            String onlyfansUserId = String.valueOf(fromUser.getId());
            String onlyfansUsername = fromUser.getUsername();
            String chatId = onlyfansUserId;
            String messageText = stripHtml(payload.getText());

            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(onlyfansUserId);
            Fan fan;

            if (fanOpt.isEmpty()) {
                fan = new Fan();
                fan.setCreatorId(creator.getCreatorId());
                fan.setContactId(onlyfansUserId);
                fan.setOnlyfansUserId(onlyfansUserId);
                fan.setOnlyfansUsername(onlyfansUsername);
                fan.setOnlyfansChatId(chatId);
                fan.setMessageCount(1);
                fan.setState("OPENING");
                fan.setLastIntent("unknown");
                fan.setTotalSpending(0.0);
                fan.setLastUpdated(LocalDateTime.now());
                fan.setCreatedAt(LocalDateTime.now());
                fan = fanService.saveFan(fan);
                log.info("Created new fan from message: {}", onlyfansUsername);
            } else {
                fan = fanOpt.get();
                fan.setMessageCount(fan.getMessageCount() + 1);
                fan.setLastUpdated(LocalDateTime.now());
                fan = fanService.saveFan(fan);
            }

            Conversation conversation = conversationService.getOrCreateConversation(fan);
            ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);

            String previousScriptCategory = state.getLastScriptCategory();
            String previousState = state.getCurrentState();

            if (payload.getFanData() != null && payload.getFanData().getSpending() != null) {
                Double totalSpending = payload.getFanData().getSpending().getTotal();
                if (totalSpending != null && totalSpending > 0) {
                    Double previousSpending = fan.getTotalSpending();
                    fan.setTotalSpending(totalSpending);
                    fanService.saveFan(fan);
                    state.setTotalSpent(totalSpending);
                    scriptEngineService.updateConversationState(state, state.getCurrentState(), state.getLastScriptCategory());

                    if (previousSpending != null && totalSpending > previousSpending && previousScriptCategory != null) {
                        double purchaseAmount = totalSpending - previousSpending;
                        scriptAnalyticsService.trackPurchase(
                            creator.getCreatorId(),
                            previousScriptCategory,
                            previousState,
                            purchaseAmount
                        );
                    }
                }
            }

            messageService.saveMessage(
                creator.getCreatorId(),
                onlyfansUserId,
                "user",
                messageText,
                payload.getCreatedAt(),
                "onlyfans"
            );

            List<Message> recentMessages = messageService.getRecentMessages(onlyfansUserId, 10);
            String analysisJson = scriptEngineService.analyzeConversationState(state, messageText, recentMessages);

            scriptEngineService.detectAndStoreFanPreferences(state, messageText);

            String scriptCategory = scriptEngineService.selectScriptCategory(state, analysisJson);
            String scriptTemplate = scriptEngineService.getScriptTemplate(scriptCategory, state);
            Map<String, String> frameworkGuidance = scriptEngineService.getFrameworkGuidance(state);

            scriptAnalyticsService.trackScriptUsage(creator.getCreatorId(), scriptCategory, state.getCurrentState());

            String conversationHistory = messageService.getConversationHistory(onlyfansUserId, 10);
            String response = anthropicService.generateScriptBasedResponse(
                messageText,
                conversationHistory,
                fan,
                state,
                scriptTemplate,
                frameworkGuidance
            );

            onlyFansApiService.sendMessage(chatId, response);

            messageService.saveMessage(
                creator.getCreatorId(),
                onlyfansUserId,
                "bot",
                response,
                LocalDateTime.now().toString(),
                "onlyfans"
            );

            JsonNode analysis = new ObjectMapper().readTree(analysisJson);
            String suggestedNextState = analysis.get("suggested_next_state").asText();
            int engagementLevel = analysis.get("engagement_level").asInt();

            scriptAnalyticsService.trackResponse(
                creator.getCreatorId(),
                scriptCategory,
                state.getCurrentState(),
                engagementLevel
            );

            scriptEngineService.updateConversationState(state, suggestedNextState, scriptCategory);

            if (scriptCategory.equals("LOCK_SALE") || scriptCategory.equals("LEAD_OPPORTUNITY")) {
                scriptEngineService.advanceFrameworkStage(state);
            }

            log.info("Successfully processed OnlyFans message from: {} with script category: {}",
                onlyfansUsername, scriptCategory);

        } catch (Exception e) {
            log.error("Error processing incoming message", e);
            errorLogService.logError(
                "ONLYFANS_MESSAGE_PROCESSING_FAILED",
                "Failed to process incoming message",
                e,
                "Webhook: " + webhook.toString()
            );
        }
    }
}

