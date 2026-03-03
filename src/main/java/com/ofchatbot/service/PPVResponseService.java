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
public class PPVResponseService {

    private final PPVOfferRepository ppvOfferRepository;
    private final OnlyFansApiService onlyFansApiService;
    private final AnthropicService anthropicService;
    private final MessageService messageService;

    public void sendFollowUpAfterPurchase(Fan fan, ConversationState state, Double purchaseAmount) {
        try {
            long minutesSincePurchase = getMinutesSinceLastPurchase(fan.getId());

            if (minutesSincePurchase < 5) {
                log.debug("Too soon to send follow-up after purchase for fan {}", fan.getId());
                return;
            }

            if (minutesSincePurchase > 60) {
                log.debug("Too late to send immediate follow-up for fan {}", fan.getId());
                return;
            }

            String systemPrompt = buildFollowUpPrompt(state, purchaseAmount);
            String userPrompt = "Generate a follow-up message checking if they enjoyed the content.";

            String followUpMessage = anthropicService.generateResponse(systemPrompt, userPrompt, null);

            onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), followUpMessage);

            messageService.saveMessage(
                fan.getCreatorId(),
                fan.getOnlyfansUserId(),
                "bot",
                followUpMessage,
                LocalDateTime.now().toString(),
                "onlyfans"
            );

            log.info("Sent PPV follow-up to fan {} after ${} purchase", fan.getId(), purchaseAmount);

        } catch (Exception e) {
            log.error("Error sending PPV follow-up", e);
        }
    }

    public boolean hasPendingUnpurchasedOffer(Long fanId) {
        List<PPVOffer> unpurchasedOffers = ppvOfferRepository.findByFanIdAndPurchasedFalse(fanId);
        
        if (unpurchasedOffers.isEmpty()) {
            return false;
        }

        PPVOffer mostRecent = unpurchasedOffers.stream()
            .max((o1, o2) -> o1.getOfferedAt().compareTo(o2.getOfferedAt()))
            .orElse(null);

        if (mostRecent == null) {
            return false;
        }

        long hoursSinceOffer = ChronoUnit.HOURS.between(mostRecent.getOfferedAt(), LocalDateTime.now());
        
        return hoursSinceOffer < 48;
    }

    public void sendGentleReminder(Fan fan, ConversationState state) {
        try {
            List<PPVOffer> unpurchasedOffers = ppvOfferRepository.findByFanIdAndPurchasedFalse(fan.getId());
            
            if (unpurchasedOffers.isEmpty()) {
                return;
            }

            PPVOffer mostRecent = unpurchasedOffers.stream()
                .max((o1, o2) -> o1.getOfferedAt().compareTo(o2.getOfferedAt()))
                .orElse(null);

            if (mostRecent == null) {
                return;
            }

            long hoursSinceOffer = ChronoUnit.HOURS.between(mostRecent.getOfferedAt(), LocalDateTime.now());
            
            if (hoursSinceOffer < 12 || hoursSinceOffer > 48) {
                return;
            }

            if (mostRecent.getFollowUpCount() >= 2) {
                log.debug("Already sent maximum follow-ups for offer to fan {}", fan.getId());
                return;
            }

            String systemPrompt = buildReminderPrompt(state, mostRecent.getPrice());
            String userPrompt = "Generate a gentle reminder about the PPV content they haven't unlocked yet.";

            String reminderMessage = anthropicService.generateResponse(systemPrompt, userPrompt, null);

            onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), reminderMessage);

            messageService.saveMessage(
                fan.getCreatorId(),
                fan.getOnlyfansUserId(),
                "bot",
                reminderMessage,
                LocalDateTime.now().toString(),
                "onlyfans"
            );

            mostRecent.setFollowUpCount(mostRecent.getFollowUpCount() + 1);
            ppvOfferRepository.save(mostRecent);

            log.info("Sent PPV reminder to fan {} for ${} offer", fan.getId(), mostRecent.getPrice());

        } catch (Exception e) {
            log.error("Error sending PPV reminder", e);
        }
    }

    private long getMinutesSinceLastPurchase(Long fanId) {
        List<PPVOffer> purchasedOffers = ppvOfferRepository.findByFanIdAndPurchasedTrue(fanId);
        
        if (purchasedOffers.isEmpty()) {
            return Long.MAX_VALUE;
        }

        PPVOffer mostRecent = purchasedOffers.stream()
            .max((o1, o2) -> o1.getPurchasedAt().compareTo(o2.getPurchasedAt()))
            .orElse(null);

        if (mostRecent == null || mostRecent.getPurchasedAt() == null) {
            return Long.MAX_VALUE;
        }

        return ChronoUnit.MINUTES.between(mostRecent.getPurchasedAt(), LocalDateTime.now());
    }

    private String buildFollowUpPrompt(ConversationState state, Double purchaseAmount) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator following up with a fan who recently purchased PPV content.
            
            CONTEXT:
            - They purchased content for $%.2f
            - Current conversation phase: %s
            - Intensity level: %d/10
            - It's been a few minutes since they unlocked the content
            
            TASK:
            Generate a casual follow-up message checking if they're enjoying the content.
            
            CRITICAL RULES:
            - Keep it SHORT (1 sentence)
            - Be playful and curious
            - Don't be pushy or salesy
            - Use 1 emoji max
            - Match the current vibe
            - Make it feel like you genuinely care about their reaction
            
            EXAMPLES:
            - "Hope you're enjoying it 😘"
            - "What do you think? 😏"
            - "Did you like it babe? 💕"
            
            Generate ONLY the follow-up message, nothing else.
            """, purchaseAmount, phase, intensity);
    }

    private String buildReminderPrompt(ConversationState state, Double offerPrice) {
        String phase = state.getCurrentPhase() != null ? state.getCurrentPhase() : "CASUAL";
        int intensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 5;

        return String.format("""
            You are a flirty OnlyFans creator gently reminding a fan about PPV content they haven't unlocked yet.
            
            CONTEXT:
            - Offer price: $%.2f
            - Current conversation phase: %s
            - Intensity level: %d/10
            - They showed interest but haven't purchased yet
            
            TASK:
            Generate a subtle, non-pushy reminder about the content.
            
            CRITICAL RULES:
            - Keep it SHORT (1-2 sentences)
            - Be playful, NOT salesy
            - Create curiosity and FOMO
            - Use 1-2 emojis max
            - Don't mention the price
            - Make it feel spontaneous, not like a reminder
            - Match the current vibe
            
            
            Generate ONLY the reminder message, nothing else.
            """, offerPrice, phase, intensity);
    }
}
