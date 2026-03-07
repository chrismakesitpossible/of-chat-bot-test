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
public class TipPromptService {

    private final OnlyFansApiService onlyFansApiService;
    private final AnthropicService anthropicService;

    private static final int HIGH_ENGAGEMENT_THRESHOLD = 7;
    private static final int MIN_MESSAGES_BEFORE_TIP = 8;
    private static final int COOLDOWN_HOURS = 24;

    public boolean shouldPromptForTip(ConversationState state, Fan fan) {
        if (state.getIntensityLevel() == null || state.getIntensityLevel() < HIGH_ENGAGEMENT_THRESHOLD) {
            log.debug("Engagement level {} below threshold {}", state.getIntensityLevel(), HIGH_ENGAGEMENT_THRESHOLD);
            return false;
        }

        if (state.getMessageCount() == null || state.getMessageCount() < MIN_MESSAGES_BEFORE_TIP) {
            log.debug("Message count {} below minimum {}", state.getMessageCount(), MIN_MESSAGES_BEFORE_TIP);
            return false;
        }

        if (fan.getLastTipPromptTime() != null) {
            long hoursSinceLastPrompt = ChronoUnit.HOURS.between(fan.getLastTipPromptTime(), LocalDateTime.now());
            if (hoursSinceLastPrompt < COOLDOWN_HOURS) {
                log.debug("Tip prompt cooldown active: {} hours since last prompt", hoursSinceLastPrompt);
                return false;
            }
        }

        if (state.getCurrentPhase() != null &&
            (state.getCurrentPhase().equals("FLIRTY") ||
             state.getCurrentPhase().equals("SUGGESTIVE") ||
             state.getCurrentPhase().equals("INTIMATE"))) {

            if (state.getMessagesSinceLastPurchase() != null && state.getMessagesSinceLastPurchase() > 5) {
                log.info("High engagement moment detected for tip prompt - intensity: {}, phase: {}",
                    state.getIntensityLevel(), state.getCurrentPhase());
                return true;
            }
        }

        return false;
    }

    public void sendTipPrompt(Fan fan, ConversationState state, String conversationContext) {
        String tipAmount = determineTipAmount(fan, state);

        String systemPrompt = buildTipPromptSystemPrompt(tipAmount, state);
        String userPrompt = buildTipPromptUserPrompt(conversationContext);

        String tipMessage = anthropicService.generateResponse(systemPrompt, userPrompt, null);

        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), tipMessage);

        fan.setLastTipPromptTime(LocalDateTime.now());

        log.info("Sent tip prompt to fan {} for ${} at engagement level {}",
            fan.getId(), tipAmount, state.getIntensityLevel());
    }

    private String determineTipAmount(Fan fan, ConversationState state) {
        double totalSpent = state.getTotalSpent() != null ? state.getTotalSpent() : 0.0;

        if (totalSpent >= 500.0) {
            return "50";
        } else if (totalSpent >= 200.0) {
            return "30";
        } else if (totalSpent >= 50.0) {
            return "20";
        } else {
            return "10";
        }
    }

    private String buildTipPromptSystemPrompt(String tipAmount, ConversationState state) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator having an engaging conversation with a fan.
            
            CONTEXT:
            - Current conversation phase: %s
            - Intensity level: %d/10
            - The conversation is at a HIGH ENGAGEMENT moment
            
            TASK:
            Generate a natural, organic tip request for $%s that flows seamlessly from the conversation.
            
            CRITICAL RULES:
            - Make it feel spontaneous and playful, NOT transactional
            - Tie it to the current conversation mood and energy
            - Keep it SHORT (1-2 sentences max)
            - Use emojis that match the vibe please please please its not all the time you use emojis 
            - Make them WANT to tip, don't demand it
            - If intensity is high (7+), be more suggestive and playful
            - If phase is INTIMATE, tie it to the intimate energy
            - Always write in ENGLISH only. Never use the fan's language.
            
            Generate ONLY the tip prompt message, nothing else.
            """, phase, intensity, tipAmount, tipAmount);
    }

    private String buildTipPromptUserPrompt(String conversationContext) {
        return String.format("""
            Recent conversation context:
            %s
            
            Generate a tip prompt that flows naturally from this conversation.
            """, conversationContext);
    }
}
