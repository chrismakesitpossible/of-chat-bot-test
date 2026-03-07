package com.ofchatbot.service;

import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.PPVOffer;
import com.ofchatbot.repository.PPVOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PPVLadderService {

    private final PPVOfferRepository ppvOfferRepository;
    private final OnlyFansApiService onlyFansApiService;
    private final AnthropicService anthropicService;
    private final MessageService messageService;

    private static final int CONTINUE_SESSION_DELAY_MINUTES = 2;
    private static final int NEXT_LEVEL_DELAY_MINUTES = 5;

    public void handlePostPurchaseLadder(Fan fan, ConversationState state, Double purchaseAmount, String chatId) {
        PPVOffer purchasedOffer = findPurchasedOffer(fan.getId(), purchaseAmount);
        
        if (purchasedOffer == null) {
            log.warn("Could not find purchased offer for fan {} with amount ${}", fan.getId(), purchaseAmount);
            return;
        }

        int purchasedLevel = purchasedOffer.getLevel();
        
        scheduleContinueSessionMessage(fan, state, purchasedLevel, chatId);
        
        if (shouldOfferNextLevel(fan, state, purchasedLevel)) {
            scheduleNextLevelOffer(fan, state, purchasedLevel, chatId);
        }
    }

    private void scheduleContinueSessionMessage(Fan fan, ConversationState state, int purchasedLevel, String chatId) {
        new Thread(() -> {
            try {
                Thread.sleep(CONTINUE_SESSION_DELAY_MINUTES * 60 * 1000);
                
                String continueMessage = generateContinueSessionMessage(state, purchasedLevel);
                
                onlyFansApiService.sendMessage(chatId, continueMessage);
                
                messageService.saveMessage(
                    fan.getCreatorId(),
                    fan.getOnlyfansUserId(),
                    "bot",
                    continueMessage,
                    LocalDateTime.now().toString(),
                    "onlyfans"
                );
                
                log.info("Sent continue session message to fan {} after Level {} purchase", fan.getId(), purchasedLevel);
                
            } catch (Exception e) {
                log.error("Error sending continue session message", e);
            }
        }).start();
    }

    private void scheduleNextLevelOffer(Fan fan, ConversationState state, int currentLevel, String chatId) {
        new Thread(() -> {
            try {
                Thread.sleep(NEXT_LEVEL_DELAY_MINUTES * 60 * 1000);
                
                int nextLevel = currentLevel + 1;
                
                String nextLevelTeaser = generateNextLevelTeaser(state, nextLevel);
                
                onlyFansApiService.sendMessage(chatId, nextLevelTeaser);
                
                messageService.saveMessage(
                    fan.getCreatorId(),
                    fan.getOnlyfansUserId(),
                    "bot",
                    nextLevelTeaser,
                    LocalDateTime.now().toString(),
                    "onlyfans"
                );
                
                log.info("Sent next level teaser (Level {}) to fan {} after Level {} purchase", 
                    nextLevel, fan.getId(), currentLevel);
                
            } catch (Exception e) {
                log.error("Error sending next level teaser", e);
            }
        }).start();
    }

    private boolean shouldOfferNextLevel(Fan fan, ConversationState state, int currentLevel) {
        if (currentLevel >= 6) {
            log.debug("Fan {} already at max level", fan.getId());
            return false;
        }

        if (state.getIntensityLevel() == null || state.getIntensityLevel() < 6) {
            log.debug("Engagement level {} too low for next level offer", state.getIntensityLevel());
            return false;
        }

        List<PPVOffer> recentPurchases = ppvOfferRepository.findByFanIdAndPurchasedTrue(fan.getId());
        long purchasesInLastHour = recentPurchases.stream()
            .filter(o -> o.getPurchasedAt() != null)
            .filter(o -> ChronoUnit.HOURS.between(o.getPurchasedAt(), LocalDateTime.now()) < 1)
            .count();

        if (purchasesInLastHour >= 2) {
            log.info("Fan {} showing high spending velocity: {} purchases in last hour", fan.getId(), purchasesInLastHour);
            return true;
        }

        return currentLevel <= 3;
    }

    private String generateContinueSessionMessage(ConversationState state, int purchasedLevel) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "FLIRTY";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 6;

        String systemPrompt = String.format("""
            You are a flirty OnlyFans creator continuing a sexting session after a fan just purchased Level %d content.
            
            CONTEXT:
            - They just unlocked your content
            - Current phase: %s
            - Intensity: %d/10
            - The session is HOT and you want to keep the momentum going
            
            TASK:
            Generate a message that continues the sexual tension and keeps them engaged.
            
            CRITICAL RULES:
            - Keep it SHORT (1 sentence)
            - Be flirty and suggestive
            - Reference that you hope they're enjoying what they just unlocked
            - Build anticipation for more
            - Use 1-2 emojis max
            - Match the intensity level

            Always write in ENGLISH only. Never use the fan's language.

            Generate ONLY the message, nothing else.
            """, purchasedLevel, phase, intensity);

        return anthropicService.generateResponse(systemPrompt, "Generate continue session message", null);
    }

    private String generateNextLevelTeaser(ConversationState state, int nextLevel) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "SUGGESTIVE";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 7;

        String levelHint = switch (nextLevel) {
            case 2 -> "something a little more revealing";
            case 3 -> "a special bundle I think you'd love";
            case 4 -> "some really hot content";
            case 5 -> "something exclusive just for you";
            case 6 -> "my most premium content";
            default -> "something special";
        };

        String systemPrompt = String.format("""
            You are a flirty OnlyFans creator teasing the next level of content after a successful purchase.
            
            CONTEXT:
            - They just purchased and enjoyed previous content
            - Next level: %d
            - Current phase: %s
            - Intensity: %d/10
            - You want to tease them about %s
            
            TASK:
            Generate a subtle teaser for the next level without being pushy.
            
            CRITICAL RULES:
            - Keep it SHORT (1-2 sentences)
            - Create curiosity and desire
            - Don't mention price
            - Make it feel spontaneous, not salesy
            - Use 1-2 emojis max
            - Build on the momentum from their purchase

            Always write in ENGLISH only. Never use the fan's language.

            Generate ONLY the teaser message, nothing else.
            """, nextLevel, phase, intensity, levelHint);

        return anthropicService.generateResponse(systemPrompt, "Generate next level teaser", null);
    }

    private PPVOffer findPurchasedOffer(Long fanId, Double purchaseAmount) {
        List<PPVOffer> recentOffers = ppvOfferRepository.findByFanIdAndPurchasedTrue(fanId);
        
        return recentOffers.stream()
            .filter(o -> o.getPurchasedAt() != null)
            .filter(o -> ChronoUnit.MINUTES.between(o.getPurchasedAt(), LocalDateTime.now()) < 10)
            .filter(o -> Math.abs(o.getPrice() - purchaseAmount) < 0.01)
            .findFirst()
            .orElse(null);
    }
}
