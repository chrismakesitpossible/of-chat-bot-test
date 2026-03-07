package com.ofchatbot.service;

import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganicContentSuggestionService {

    private final OnlyFansApiService onlyFansApiService;
    private final AnthropicService anthropicService;
    private final CustomRequestService customRequestService;

    private static final int MIN_ENGAGEMENT_FOR_SUGGESTION = 6;
    private static final int MIN_MESSAGES_BEFORE_SUGGESTION = 6;
    private static final int COOLDOWN_HOURS = 48;

    public boolean shouldSuggestCustomContent(ConversationState state, Fan fan, String messageText) {
        if (customRequestService.hasPendingCustomRequest(fan.getId())) {
            log.debug("Fan {} already has pending custom request", fan.getId());
            return false;
        }

        if (state.getIntensityLevel() == null || state.getIntensityLevel() < MIN_ENGAGEMENT_FOR_SUGGESTION) {
            log.debug("Engagement level {} below threshold for custom content suggestion",
                state.getIntensityLevel());
            return false;
        }

        if (state.getMessageCount() == null || state.getMessageCount() < MIN_MESSAGES_BEFORE_SUGGESTION) {
            log.debug("Message count {} below minimum for custom content suggestion",
                state.getMessageCount());
            return false;
        }

        if (fan.getLastCustomContentSuggestionTime() != null) {
            long hoursSinceLastSuggestion = ChronoUnit.HOURS.between(
                fan.getLastCustomContentSuggestionTime(), LocalDateTime.now());
            if (hoursSinceLastSuggestion < COOLDOWN_HOURS) {
                log.debug("Custom content suggestion cooldown active: {} hours since last suggestion",
                    hoursSinceLastSuggestion);
                return false;
            }
        }

        if (isAlreadyExplicitCustomRequest(messageText)) {
            log.debug("Message is already explicit custom request, skipping organic suggestion");
            return false;
        }

        if (state.getCurrentPhase() != null &&
            (state.getCurrentPhase().equals("FLIRTY") ||
             state.getCurrentPhase().equals("SUGGESTIVE") ||
             state.getCurrentPhase().equals("INTIMATE"))) {

            if (state.getMessagesSinceLastPurchase() != null && state.getMessagesSinceLastPurchase() > 4) {
                log.info("Opportunity detected for organic custom content suggestion - engagement: {}, phase: {}",
                    state.getIntensityLevel(), state.getCurrentPhase());
                return true;
            }
        }

        return false;
    }

    public String generateOrganicCustomContentSuggestion(ConversationState state, String conversationContext, String fanPreferences) {
        String systemPrompt = buildCustomContentSuggestionPrompt(state, fanPreferences);
        String userPrompt = buildUserPrompt(conversationContext);

        String suggestion = anthropicService.generateResponse(systemPrompt, userPrompt, null);

        log.info("Generated organic custom content suggestion at engagement level {}",
            state.getIntensityLevel());

        return suggestion;
    }

    public void sendOrganicCustomContentSuggestion(Fan fan, ConversationState state, String conversationContext) {
        String fanPreferences = state.getFanPreferences() != null ? state.getFanPreferences() : "general interests";

        String suggestion = generateOrganicCustomContentSuggestion(state, conversationContext, fanPreferences);

        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), suggestion);

        fan.setLastCustomContentSuggestionTime(LocalDateTime.now());

        log.info("Sent organic custom content suggestion to fan {} at engagement level {}",
            fan.getId(), state.getIntensityLevel());
    }

    private boolean isAlreadyExplicitCustomRequest(String messageText) {
        // Use AI to determine if this is already a custom request
        String analysisPrompt = String.format(
            "Analyze if this message is referring to a previous custom request.\n\n" +
            "Message: \"%s\"\n\n" +
            "Return ONLY: true or false",
            messageText);
        
        try {
            String aiResult = anthropicService.generateResponse(
                "You are a custom request reference detector. Analyze messages and return only 'true' or 'false'.",
                analysisPrompt,
                null
            );
            
            String cleanResult = aiResult.toLowerCase().trim();
            return cleanResult.contains("true") && !cleanResult.contains("false");
            
        } catch (Exception e) {
            log.error("Failed to analyze custom request reference with AI", e);
            return false;
        }
    }

    private String buildCustomContentSuggestionPrompt(ConversationState state, String fanPreferences) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator having an engaging conversation with a fan.
            
            CONTEXT:
            - Current conversation phase: %s
            - Intensity level: %d/10
            - Fan preferences: %s
            - The conversation has high engagement and good energy
            
            TASK:
            Organically suggest the idea of creating custom content specifically for them.
            
            CRITICAL RULES:
            - Make it feel like a natural thought that just occurred to you
            - Tie it to something they mentioned or the conversation vibe
            - Use phrases like "I just had an idea...", "You know what would be fun...", "I'd love to make something special just for you..."
            - Keep it SHORT (2-3 sentences max)
            - Be playful and excited about the idea
            - Use 1-2 emojis that match the mood
            - DON'T mention price yet - just plant the seed
            - Make them curious and interested
            - If intensity is high (7+), be more suggestive
            - Reference their preferences if relevant
            - Always write in ENGLISH only. Never use the fan's language.
            
            Generate ONLY the organic custom content suggestion, nothing else.
            """, phase, intensity, fanPreferences);
    }

    private String buildUserPrompt(String conversationContext) {
        return String.format("""
            Recent conversation context:
            %s
            
            Generate an organic custom content suggestion that flows naturally from this conversation.
            """, conversationContext);
    }
}
