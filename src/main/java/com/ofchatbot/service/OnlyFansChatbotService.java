package com.ofchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.dto.OnlyFansWebhookPayload;
import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.entity.CustomRequest;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final ResponseTimingService responseTimingService;
    private final PPVService ppvService;
    private final CustomRequestService customRequestService;
    private final PeakInterestDetectionService peakInterestDetectionService;
    private final OrganicContentSuggestionService organicContentSuggestionService;
    private final TipPromptService tipPromptService;
    private final ScriptService scriptService;

    @Value("${pricing.cold-days:7}")
    private int coldDays = 7;

    /** Decline cooldown: no new PPV offers for this many hours after fan declines (Issue #4.4). */
    private static final int DECLINE_COOLDOWN_HOURS = 2;

    private static final Pattern DECLINE_PATTERN = Pattern.compile(
        "(?i)(\\bno\\b|not now|too expensive|too much|can'?t afford|pass\\b|nah\\b|nope\\b|maybe later|not interested|i'?m good)"
    );

    public void processNewSubscription(OnlyFansWebhookPayload webhook, Creator creator) {
            try {
                log.info("Processing new OnlyFans subscription for creator: {}", creator.getName());

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
                    if (user.getName() != null && !user.getName().isBlank()) {
                        newFan.setOnlyfansDisplayName(user.getName().trim());
                    }
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

                    onlyFansApiService.sendMessage(chatId, welcomeMessage, null, creator.getCreatorId());

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


    public void processIncomingMessage(OnlyFansWebhookPayload webhook, Creator creator) {
        try {
            log.info("Processing incoming OnlyFans message for creator: {}", creator.getName());

            OnlyFansWebhookPayload.PayloadData payload = webhook.getPayload();
            OnlyFansWebhookPayload.PayloadData.FromUser fromUser = payload.getFromUser();

            String onlyfansUserId = String.valueOf(fromUser.getId());
            String onlyfansUsername = fromUser.getUsername();
            String chatId = onlyfansUserId;
            String messageText = stripHtml(payload.getText());

            Optional<Fan> fanOpt = fanService.findByOnlyfansUserId(onlyfansUserId);

            Fan fan;
            boolean reengagingAfterCold = false;

            if (fanOpt.isEmpty()) {
                fan = new Fan();
                fan.setCreatorId(creator.getCreatorId());
                fan.setContactId(onlyfansUserId);
                fan.setOnlyfansUserId(onlyfansUserId);
                fan.setOnlyfansUsername(onlyfansUsername);
                if (fromUser.getName() != null && !fromUser.getName().isBlank()) {
                    fan.setOnlyfansDisplayName(fromUser.getName().trim());
                }
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
                reengagingAfterCold = isReengagingAfterCold(fan);
                fan.setMessageCount(fan.getMessageCount() + 1);
                fan.setLastUpdated(LocalDateTime.now());
                fan.setOnlyfansUsername(onlyfansUsername);
                if (fromUser.getName() != null && !fromUser.getName().isBlank()) {
                    fan.setOnlyfansDisplayName(fromUser.getName().trim());
                }
                fan = fanService.saveFan(fan);
            }

            updateActiveReplierIfNeeded(fan, onlyfansUserId, payload.getCreatedAt());
            tryExtractAndStoreCountry(fan, messageText);

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

            String externalMessageId = payload.getId() != null ? String.valueOf(payload.getId()) : null;
            
            messageService.saveMessage(
                creator.getCreatorId(),
                onlyfansUserId,
                "user",
                messageText,
                payload.getCreatedAt(),
                "onlyfans",
                externalMessageId
            );

            // Backfill past conversation from OF API if this fan has no prior messages in DB
            List<Message> existingMessages = messageService.getRecentMessages(onlyfansUserId, 10);
            if (existingMessages.size() <= 1) {
                try {
                    String chatHistory = onlyFansApiService.fetchChatHistory(chatId, creator.getCreatorId());
                    if (chatHistory != null) {
                        int imported = messageService.importChatHistory(chatHistory, creator.getCreatorId(), onlyfansUserId, creator.getCreatorId());
                        if (imported > 0) {
                            log.info("Backfilled {} past messages for fan {}", imported, onlyfansUserId);
                        }
                    }
                } catch (Exception histEx) {
                    log.warn("Failed to backfill chat history for fan {} (non-critical): {}", onlyfansUserId, histEx.getMessage());
                }
            }

            List<Message> recentMessages = messageService.getRecentMessages(onlyfansUserId, 10);

            // Get all recent fan messages sent within last 2 minutes for batched context
            String batchedMessageText = messageText;
            long recentFanMessageCount = recentMessages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .filter(m -> java.time.Duration.between(m.getTimestamp(), java.time.LocalDateTime.now()).toMinutes() < 2)
                .count();
            
            if (recentFanMessageCount > 1) {
                // Combine recent fan messages for analysis
                StringBuilder batchedMessages = new StringBuilder();
                recentMessages.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .filter(m -> java.time.Duration.between(m.getTimestamp(), java.time.LocalDateTime.now()).toMinutes() < 2)
                    .forEach(m -> {
                        if (batchedMessages.length() > 0) {
                            batchedMessages.append("\n");
                        }
                        batchedMessages.append(m.getText());
                    });
                batchedMessageText = batchedMessages.toString();
                log.info("Batched {} messages into: {}", recentFanMessageCount, batchedMessageText);
            }

            // Detect explicit purchase intent once so we can drive both PPV and reply behaviour
            boolean explicitPurchaseIntent = hasExplicitPurchaseIntent(batchedMessageText);
            // If they mentioned a price (e.g. "send for $60"), use it as minimum PPV tier
            Double fanMentionedPrice = parseMentionedPrice(batchedMessageText);
            // Detect purchase complaints / scam concerns so we can pause sales and focus on fixing issues.
            boolean purchaseComplaint = isPurchaseComplaintOrScamConcern(batchedMessageText);
            
            String analysisJson = null;
            String scriptCategory = "CASUAL";
            String suggestedNextState = state.getCurrentState();
            int engagementLevel = 5;

            try {
                analysisJson = scriptEngineService.analyzeConversationState(state, batchedMessageText, recentMessages);
                scriptEngineService.detectAndStoreFanPreferences(state, batchedMessageText);
                scriptCategory = scriptEngineService.selectScriptCategory(state, analysisJson, recentMessages);
            } catch (Exception analysisEx) {
                log.error("AI analysis failed — using defaults. Fan: {}", onlyfansUserId, analysisEx);
            }

            String scriptTemplate = null;
            Map<String, String> frameworkGuidance = null;
            String conversationHistory = null;
            try {
                scriptTemplate = scriptEngineService.getScriptTemplate(scriptCategory, state);
                frameworkGuidance = scriptEngineService.getFrameworkGuidance(state);
            } catch (Exception templateEx) {
                log.error("Script template/framework retrieval failed — using defaults. Fan: {}", onlyfansUserId, templateEx);
            }

            try {
                scriptAnalyticsService.trackScriptUsage(creator.getCreatorId(), scriptCategory, state.getCurrentState());
            } catch (Exception trackEx) {
                log.warn("Script analytics tracking failed (non-critical). Fan: {}", onlyfansUserId, trackEx);
            }

            try {
                conversationHistory = messageService.getConversationHistory(onlyfansUserId, 10);
            } catch (Exception histEx) {
                log.warn("Failed to fetch conversation history — proceeding without. Fan: {}", onlyfansUserId, histEx);
            }
            log.info("Batched message text being sent to AI: {}", batchedMessageText);

            // Always generate a conversational response — even with purchase intent.
            // PPV will follow separately if appropriate; never leave the fan in silence.
            {
                String response = null;
                try {
                    response = anthropicService.generateScriptBasedResponse(
                        batchedMessageText,
                        conversationHistory != null ? conversationHistory : "",
                        fan,
                        state,
                        scriptTemplate,
                        frameworkGuidance
                    );
                } catch (Exception genEx) {
                    log.error("AI response generation failed — using fallback. Fan: {}", onlyfansUserId, genEx);
                }

                // If AI returned nothing or empty, use fallback so fan never gets silence
                if (response == null || response.isBlank()) {
                    response = generateFallbackResponse(batchedMessageText);
                    log.warn("AI returned empty response for fan {} — using fallback", onlyfansUserId);
                }

                String replyToMessageId = shouldUseReplyTo(state, scriptCategory) ? externalMessageId : null;

                responseTimingService.scheduleNaturalResponse(chatId, response, state, fan, batchedMessageText, replyToMessageId, creator.getCreatorId());

                try {
                    messageService.saveMessage(
                        creator.getCreatorId(),
                        onlyfansUserId,
                        "bot",
                        response,
                        LocalDateTime.now().toString(),
                        "onlyfans",
                        null
                    );
                } catch (Exception saveEx) {
                    log.error("Failed to save bot response to DB (message was still sent). Fan: {}", onlyfansUserId, saveEx);
                }
            }

            // Parse analysis JSON safely — don't crash if malformed
            try {
                if (analysisJson != null) {
                    JsonNode analysis = new ObjectMapper().readTree(analysisJson);
                    if (analysis.has("suggested_next_state")) {
                        suggestedNextState = analysis.get("suggested_next_state").asText();
                    }
                    if (analysis.has("engagement_level")) {
                        engagementLevel = analysis.get("engagement_level").asInt();
                    }
                    if (analysis.has("current_phase")) {
                        state.setCurrentPhase(analysis.get("current_phase").asText());
                    }
                }
            } catch (Exception jsonEx) {
                log.warn("Failed to parse analysis JSON — using defaults. Fan: {}", onlyfansUserId, jsonEx);
            }

            scriptAnalyticsService.trackResponse(
                creator.getCreatorId(),
                scriptCategory,
                state.getCurrentState(),
                engagementLevel
            );

            state.setMessageCount(state.getMessageCount() != null ? state.getMessageCount() + 1 : 1);

            scriptEngineService.updateConversationState(state, suggestedNextState, scriptCategory);

            if (scriptCategory.equals("LOCK_SALE") || scriptCategory.equals("LEAD_OPPORTUNITY")) {
                scriptEngineService.advanceFrameworkStage(state);
            }

            boolean isPeakMoment = peakInterestDetectionService.isPeakInterestMoment(state, recentMessages);
            
            if (purchaseComplaint) {
                log.info("Purchase complaint / scam concern detected for fan {}. Pausing sales flows.", fan.getId());
                handlePurchaseComplaint(fan, batchedMessageText);
            } else {
                boolean shouldSendPPV = shouldSendPPVOffer(state, scriptCategory, batchedMessageText, explicitPurchaseIntent);
                
                boolean specificOrNicheRequest = explicitPurchaseIntent || isCustomRequest(batchedMessageText);
                final Fan fanForPpv = fan;
                final boolean reengagingForPpv = reengagingAfterCold;
                if (isPeakMoment) {
                    String peakReason = peakInterestDetectionService.getPeakInterestReason(state, recentMessages);
                    log.info("Peak interest moment detected for fan {}: {}", fan.getId(), peakReason);
                    
                    if (shouldSendPPV) {
                        // When fan explicitly asked for content/send/preview/$60, always use main PPV — never content script.
                        if (explicitPurchaseIntent) {
                            final Double priceHint = fanMentionedPrice;
                            responseTimingService.scheduleDelayedPPVWithTyping(chatId, creator.getCreatorId(), () ->
                                ppvService.sendPPVOffer(fanForPpv, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingForPpv, priceHint));
                        } else if (scriptService.isOnScriptNotCompleted(fan.getId())) {
                            if (!scriptService.sendScriptPPVOffer(fan, chatId, recentMessages)) {
                                ppvService.sendPPVOffer(fan, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingAfterCold);
                            }
                        } else {
                            ppvService.sendPPVOffer(fan, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingAfterCold);
                        }
                    }
                } else if (shouldSendPPV) {
                    // When fan explicitly asked for content/send/preview/$60, always use main PPV — never content script.
                    if (explicitPurchaseIntent) {
                        final Double priceHint = fanMentionedPrice;
                        responseTimingService.scheduleDelayedPPVWithTyping(chatId, creator.getCreatorId(), () -> {
                            ppvService.sendPPVOffer(fanForPpv, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingForPpv, priceHint);
                        });
                    } else if (scriptService.isOnScriptNotCompleted(fan.getId())) {
                        if (!scriptService.sendScriptPPVOffer(fan, chatId, recentMessages)) {
                            ppvService.sendPPVOffer(fan, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingAfterCold);
                        }
                    } else {
                        ppvService.sendPPVOffer(fan, state, conversation, chatId, recentMessages, specificOrNicheRequest, reengagingAfterCold);
                    }
                }
                
                // When they're explicitly asking for content / another video,
                // rely on the PPV flow instead of also kicking off a custom-request thread.
                if (!explicitPurchaseIntent && isCustomRequest(batchedMessageText)) {
                    CustomRequest pendingCustom = customRequestService.getPendingCustomRequest(fan.getId());
                    if (pendingCustom != null && "pending".equals(pendingCustom.getStatus())) {
                        handleCustomRequestFollowUp(fan, batchedMessageText);
                    } else if (pendingCustom != null && ("quoted".equals(pendingCustom.getStatus()) || "advance_paid".equals(pendingCustom.getStatus()))) {
                        // Already quoted or paid advance — don't start another custom
                    } else {
                        handleCustomRequest(fan, batchedMessageText);
                    }
                } else if (organicContentSuggestionService.shouldSuggestCustomContent(state, fan, batchedMessageText)) {
                    String recentConversationHistory = messageService.getConversationHistory(onlyfansUserId, 5);
                    organicContentSuggestionService.sendOrganicCustomContentSuggestion(fan, state, recentConversationHistory);
                    fanService.saveFan(fan);
                }
                
                if (tipPromptService.shouldPromptForTip(state, fan)) {
                    String recentConversationHistory = messageService.getConversationHistory(onlyfansUserId, 5);
                    tipPromptService.sendTipPrompt(fan, state, recentConversationHistory);
                    fanService.saveFan(fan);
                }
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
    
    private boolean shouldUseReplyTo(ConversationState state, String scriptCategory) {
        if (scriptCategory == null) return false;
        
        if (scriptCategory.equals("PPV_OFFER") || scriptCategory.equals("LOCK_SALE")) {
            return true;
        }
        
        if (state.getIntensityLevel() != null && state.getIntensityLevel() >= 5) {
            return true;
        }
        
        return Math.random() < 0.3;
    }
    
    /**
     * Parse true/false from AI classifier response. Model sometimes returns
     * conversational text plus "-> true" or "-> false"; we extract the intended boolean.
     */
    private boolean parseBooleanFromAiResponse(String aiResult) {
        if (aiResult == null || aiResult.isBlank()) {
            return false;
        }
        String s = aiResult.toLowerCase().trim();
        // Explicit "-> true" / "-> false" (model often appends this)
        if (s.contains("-> true")) return true;
        if (s.contains("-> false")) return false;
        // "answer: true" / "answer is true" etc.
        if (s.matches("(?s).*answer\\s*:?\\s*true\\b.*")) return true;
        if (s.matches("(?s).*answer\\s*:?\\s*false\\b.*")) return false;
        // Last occurrence of word "true" or "false" (model often puts answer at end)
        Pattern word = Pattern.compile("\\b(true|false)\\b");
        Matcher m = word.matcher(s);
        int lastTrue = -1, lastFalse = -1;
        while (m.find()) {
            if (m.group(1).equals("true")) lastTrue = m.start();
            else lastFalse = m.start();
        }
        if (lastTrue >= 0 || lastFalse >= 0) {
            return lastTrue > lastFalse;
        }
        // Exact or prefix
        if (s.equals("true") || s.startsWith("true")) return true;
        if (s.equals("false") || s.startsWith("false")) return false;
        return false;
    }

    /** Parse a price mentioned by the fan (e.g. "send for $60", "60 dollars", "$20") for PPV tier hint. Returns null if none found. */
    private Double parseMentionedPrice(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) return null;
        // $60, $20.95, 60 dollars, for 60, send for $60
        Matcher m = Pattern.compile("\\$\\s*(\\d+(?:\\.\\d+)?)|(\\d+)\\s*(?:dollars?|bucks?|\\$)").matcher(messageText);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            if (g != null) {
                try {
                    double v = Double.parseDouble(g);
                    if (v >= 1 && v <= 1000) return v;
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    // Regex classifiers replace API calls — saves 3-5 API calls per message (Issue #4)
    private static final Pattern PURCHASE_INTENT_PATTERN = Pattern.compile(
        "(?i)(send (me |it|them)|show me (something|more|content|vid|pic|photo)" +
        "|got any (content|vid|pic|photo)|have any (content|vid|pic|photo)" +
        "|what (do you |content|videos?|pics?|photos?)" +
        "|i('ll| will| wanna| want to) (buy|pay|unlock|purchase)" +
        "|(i )?wanna see (your|you|more|some)" +
        "|let me see|i want to see (your|you|more)" +
        "|how much|what('s| is) (the price|it cost)" +
        "|another (one|vid|video|pic|photo)" +
        "|more (content|videos?|pics?|photos?)" +
        "|can i (see|get|buy|have) (some|more|your|the|a)" +
        "|\\$\\d+|send for)"
    );

    private boolean hasExplicitPurchaseIntent(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) return false;
        boolean result = PURCHASE_INTENT_PATTERN.matcher(messageText).find();
        if (result) log.info("Purchase intent detected (regex) for: '{}'", messageText);
        return result;
    }

    private static final Pattern COMPLAINT_PATTERN = Pattern.compile(
        "(?i)(scam|fake|didn'?t (receive|get)|never (got|received)" +
        "|chargeback|dispute|report (you|this)" +
        "|rip.?off|where('s| is) (my|the) (content|video|pic|photo|set)" +
        "|still waiting|paid but|paid and|i paid|my money)"
    );

    private boolean isPurchaseComplaintOrScamConcern(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) return false;
        boolean result = COMPLAINT_PATTERN.matcher(messageText).find();
        if (result) log.info("Purchase complaint detected (regex) for: '{}'", messageText);
        return result;
    }

    private void handlePurchaseComplaint(Fan fan, String messageText) {
        String systemPrompt = """
            You are responding to a fan who is complaining about a purchase (missing content, scam concern, or not receiving what they paid for).

            CRITICAL RULES:
            - DO NOT try to sell anything or mention prices.
            - DO NOT send any new PPV offer.
            - Apologize clearly and sincerely.
            - Acknowledge that you understand they feel they did not get what they paid for.
            - Reassure them you want to make it right and fix the issue.
            - Keep it SHORT (1-3 sentences).
            - Calm their suspicion: emphasize you're real and care about them getting their content.
            - Invite them to clarify which set or purchase they're talking about if it's not clear.

            Generate ONLY the message text, nothing else.
            """;

        String response = anthropicService.generateResponse(
            systemPrompt,
            "Fan message: " + messageText,
            null
        );

        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), response);

        log.info("Handled purchase complaint / scam concern for fan {}", fan.getId());
    }

    private boolean shouldSendPPVOffer(ConversationState state, String scriptCategory, String messageText, boolean explicitPurchaseIntent) {
        // Only send PPV when the fan explicitly asks for content.
        // No proactive/out-of-the-blue PPV sends — fans find that spammy.
        if (!explicitPurchaseIntent) {
            return false;
        }

        // Decline cooldown: if fan just declined, back off (Issue #4.4)
        if (DECLINE_PATTERN.matcher(messageText).find()) {
            log.info("Fan declined PPV — setting {}-hour cooldown", DECLINE_COOLDOWN_HOURS);
            state.setLastPurchaseTime(LocalDateTime.now()); // reuse field as cooldown marker
            return false;
        }
        if (state.getLastPurchaseTime() != null &&
            ChronoUnit.HOURS.between(state.getLastPurchaseTime(), LocalDateTime.now()) < DECLINE_COOLDOWN_HOURS &&
            (state.getTotalSpent() == null || state.getTotalSpent() == 0)) {
            log.info("PPV decline cooldown active — skipping offer");
            return false;
        }

        log.info("Explicit purchase intent detected, sending PPV offer");
        return true;
    }
    
    private boolean isMediaRequest(String messageText) {
        // Reuse purchase intent pattern — media requests are a subset of purchase intent
        return hasExplicitPurchaseIntent(messageText);
    }
    
    private static final Pattern CUSTOM_REQUEST_PATTERN = Pattern.compile(
        "(?i)(custom|personali[sz]ed|make (me |something)|just for me" +
        "|say my name|with my name|can you (make|film|record|do)" +
        "|specific (content|video)|request a video)"
    );

    private boolean isCustomRequest(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) return false;
        boolean result = CUSTOM_REQUEST_PATTERN.matcher(messageText).find();
        if (result) log.info("Custom request detected (regex) for: '{}'", messageText);
        return result;
    }

    private void handleCustomRequest(Fan fan, String messageText) {
        String askForDetailsPrompt = """
            You are responding to a fan who seems interested in custom content.

            CRITICAL RULES:
            - Ask them what they'd like to see in the custom video
            - Keep it SHORT (1-2 sentences)
            - Be flirty and excited about making something special for them
            - Use 1-2 emojis max
            - Make them feel special
            - Always respond in ENGLISH only. Never use the fan's language (e.g. Tagalog, Spanish). Write in English.

            Generate ONLY the message asking for details, nothing else.
            """;

        String response = anthropicService.generateResponse(
            askForDetailsPrompt,
            "Fan message: " + messageText,
            null
        );

        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), response);

        customRequestService.processCustomRequest(fan, messageText, "Pending details from fan");

        log.info("Initiated custom request conversation with fan {}", fan.getId());
    }

    /** Fan already has a pending custom (status pending) and sent a follow-up (e.g. details or duration). Send the price quote. */
    private void handleCustomRequestFollowUp(Fan fan, String messageText) {
        CustomRequest pending = customRequestService.getPendingCustomRequest(fan.getId());
        if (pending == null || !"pending".equals(pending.getStatus())) {
            return;
        }
        int durationMinutes = CustomRequestService.parseDurationFromMessage(messageText);
        customRequestService.quotePrice(pending, durationMinutes, fan);
        log.info("Sent custom quote to fan {}: {} minutes", fan.getId(), durationMinutes);
    }

    /** If fan replied within X min to at least one of our last 2–3 messages, mark as active (lastQuickReplyAt). */
    private void updateActiveReplierIfNeeded(Fan fan, String contactId, String fanMessageCreatedAt) {
        if (fanMessageCreatedAt == null || fanMessageCreatedAt.isBlank()) return;
        LocalDateTime fanTime = parseCreatedAt(fanMessageCreatedAt);
        List<Message> lastBot = messageService.getLastBotMessages(contactId, 3);
        int thresholdMinutes = 10;
        for (Message botMsg : lastBot) {
            long minutesBetween = java.time.Duration.between(botMsg.getTimestamp(), fanTime).toMinutes();
            if (minutesBetween >= 0 && minutesBetween <= thresholdMinutes) {
                fan.setLastQuickReplyAt(fanTime);
                fanService.saveFan(fan);
                log.debug("Fan {} marked as active replier (replied within {} min)", fan.getId(), minutesBetween);
                return;
            }
        }
    }

    private LocalDateTime parseCreatedAt(String createdAt) {
        try {
            return LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                log.trace("Could not parse createdAt: {}", createdAt);
                return LocalDateTime.now();
            }
        }
    }

    /** True when fan's last activity was cold-days+ ago (re-engaging after going cold → $9.95–$19.95 re-warm). */
    private boolean isReengagingAfterCold(Fan fan) {
        if (fan.getLastUpdated() == null) return false;
        long daysSince = ChronoUnit.DAYS.between(fan.getLastUpdated(), LocalDateTime.now());
        if (daysSince >= coldDays) {
            log.info("Fan {} re-engaging after {} days cold", fan.getId(), daysSince);
            return true;
        }
        return false;
    }

    /** Generate a simple fallback response when AI is down, so the fan doesn't get silence. */
    private String generateFallbackResponse(String fanMessage) {
        String[] fallbacks = {
            "hey :) give me a sec, just got caught up with something",
            "hiii sorry one moment haha",
            "heyyy hold on one sec for me",
            "hey babe let me get back to you in a min :)"
        };
        return fallbacks[new java.util.Random().nextInt(fallbacks.length)];
    }

    /** Try to extract country from message (e.g. "I'm from the US", "UK", "America") or infer from language; store on fan. */
    private void tryExtractAndStoreCountry(Fan fan, String messageText) {
        if (fan.getCountry() != null && !fan.getCountry().isBlank()) return;
        if (messageText == null || messageText.isBlank()) return;
        String lower = messageText.trim().toLowerCase();
        String found = null;
        if (lower.matches(".*\\b(from|in|live in)\\s+(the\\s+)?(us|usa|america|united states)\\b.*")) found = "US";
        else if (lower.matches(".*\\b(from|in|live in)\\s+(the\\s+)?(uk|britain|england)\\b.*")) found = "UK";
        else if (lower.matches(".*\\b(from|in|live in)\\s+(the\\s+)?(australia|au)\\b.*")) found = "AU";
        else if (lower.matches(".*\\b(from|in|live in)\\s+(the\\s+)?(canada|ca)\\b.*")) found = "CA";
        else if (lower.matches(".*\\b(from|in|live in)\\s+(the\\s+)?(germany|de)\\b.*")) found = "DE";
        else if (lower.matches(".*\\b(us|usa|america)\\b.*")) found = "US";
        else if (lower.matches(".*\\b(uk|britain|england)\\b.*")) found = "UK";
        else if (lower.matches(".*\\b(australia|au)\\b.*")) found = "AU";
        else if (lower.matches(".*\\b(canada|ca)\\b.*")) found = "CA";
        else if (lower.matches(".*\\b(germany|de)\\b.*")) found = "DE";
        if (found != null) {
            fan.setCountry(found);
            fanService.saveFan(fan);
            log.info("Stored country for fan {}: {}", fan.getId(), found);
        }
    }

}
