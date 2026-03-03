package com.ofchatbot.service;

import com.ofchatbot.entity.*;
import com.ofchatbot.exception.CannotMessageUserException;
import com.ofchatbot.repository.PPVOfferRepository;
import com.ofchatbot.repository.FanRepository;
import com.ofchatbot.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpService {
    
    private final PPVOfferRepository ppvOfferRepository;
    private final FanRepository fanRepository;
    private final ConversationRepository conversationRepository;
    private final AnthropicService anthropicService;
    private final OnlyFansApiService onlyFansApiService;
    private final MessageService messageService;
    
    @Scheduled(fixedRate = 60000)
    public void processFollowUps() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);
        LocalDateTime tenMinutesAgo = now.minusMinutes(10);
        // We now only ever send a single follow-up per offer.
        List<PPVOffer> offersNeedingFollowUp = ppvOfferRepository.findOffersNeedingFollowUp(fiveMinutesAgo);
        
        for (PPVOffer offer : offersNeedingFollowUp) {
            try {
                LocalDateTime sentAt = offer.getSentAt();
                int followUpCount = offer.getFollowUpCount();
                
                if (followUpCount == 0 && sentAt.isBefore(fiveMinutesAgo)) {
                    sendFirstFollowUp(offer);
                }
            } catch (Exception e) {
                log.error("Failed to process follow-up for offer {}", offer.getId(), e);
            }
        }
    }
    
    private void sendFirstFollowUp(PPVOffer offer) {
        Fan fan = fanRepository.findById(offer.getFanId()).orElse(null);
        if (fan == null) {
            log.warn("Fan not found for offer {}", offer.getId());
            return;
        }
        
        Conversation conversation = conversationRepository.findByFanId(fan.getId()).stream().findFirst().orElse(null);
        if (conversation == null) {
            log.warn("Conversation not found for fan {}", fan.getId());
            return;
        }
        
        String followUpMessage = generateNaturalFollowUp(offer, conversation);
        String chatId = fan.getOnlyfansChatId();

        try {
            onlyFansApiService.sendMessage(chatId, followUpMessage);
        } catch (CannotMessageUserException e) {
            // OnlyFans says we can't message this user (blocked, restricted, unsubscribed). Mark follow-up as attempted so we stop retrying.
            offer.setFollowUpCount(1);
            ppvOfferRepository.save(offer);
            log.warn("Cannot message user (chat {}). Marked offer {} follow-up as attempted so we stop retrying.", chatId, offer.getId());
            return;
        }

        offer.setFollowUpCount(1);
        ppvOfferRepository.save(offer);
        log.info("Sent first follow-up for offer {} to fan {}", offer.getId(), fan.getId());
    }
    
    private void sendGuiltTrap(PPVOffer offer) {
        Fan fan = fanRepository.findById(offer.getFanId()).orElse(null);
        if (fan == null) {
            log.warn("Fan not found for offer {}", offer.getId());
            return;
        }
        
        Conversation conversation = conversationRepository.findByFanId(fan.getId()).stream().findFirst().orElse(null);
        if (conversation == null) {
            log.warn("Conversation not found for fan {}", fan.getId());
            return;
        }
        
        String guiltTrapMessage = generateGuiltTrap(offer, conversation);
        
        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), guiltTrapMessage);
        
        offer.setFollowUpCount(2);
        ppvOfferRepository.save(offer);
        
        log.info("Sent guilt trap for offer {} to fan {}", offer.getId(), fan.getId());
    }
    
    private String generateNaturalFollowUp(PPVOffer offer, Conversation conversation) {
        String conversationContext = "No conversation history";
        if (conversation != null) {
            try {
                String history = messageService.getConversationHistory(conversation.getContactId(), 50);
                if (history != null && !history.isBlank()) {
                    conversationContext = history;
                }
            } catch (Exception e) {
                log.warn("Failed to load conversation history for conversation {}", conversation.getId(), e);
            }
        }

        String analysisPrompt = String.format(
            "Generate a natural follow-up message for a PPV offer (sent ~5 min ago).\n\n" +
            "Offer Details:\n" +
            "- Level: %d\n" +
            "- Price: $%.2f\n" +
            "- Sent: 5 minutes ago\n\n" +
            "Conversation Context (use only for tone/energy; do NOT react to or quote specific lines):\n%s\n\n" +
            "CRITICAL RULES:\n" +
            "- The message must ONLY be about the offer: playful nudge to unlock, curiosity, or \"still thinking about it?\".\n" +
            "- NEVER say anything about: robots, bots, automation, \"calling me a robot\", \"what are you talking about\", \"confused\", or any meta reply to the chat.\n" +
            "- Do NOT react to or answer random lines from the conversation. You are only sending a short follow-up for the PPV.\n" +
            "Generate a SHORT (1-2 sentences) natural follow-up that:\n" +
            "- Creates curiosity or playful urgency about the content\n" +
            "- Uses 1 emoji max\n" +
            "- Sounds spontaneous, not automated\n" +
            "- No hard selling or desperation\n\n" +
            "Respond with ONLY the message text.",
            offer.getLevel(),
            offer.getPrice(),
            conversationContext
        );
        
        try {
            return anthropicService.generateResponse(
                "You generate PPV follow-up messages only. Output only the message text. Never mention robots, bots, confusion, or meta-replies.",
                analysisPrompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate follow-up message", e);
            return "Still thinking about it? 😏";
        }
    }
    
    private String generateGuiltTrap(PPVOffer offer, Conversation conversation) {
        String conversationContext = "No conversation history";
        if (conversation != null) {
            try {
                String history = messageService.getConversationHistory(conversation.getContactId(), 50);
                if (history != null && !history.isBlank()) {
                    conversationContext = history;
                }
            } catch (Exception e) {
                log.warn("Failed to load conversation history for conversation {}", conversation.getId(), e);
            }
        }

        String analysisPrompt = String.format(
            "Generate a guilt trap message for a PPV offer (sent 8-10 min ago, they haven't unlocked).\n\n" +
            "Offer Details:\n" +
            "- Level: %d\n" +
            "- Price: $%.2f\n\n" +
            "Conversation Context (use only for tone; do NOT react to or quote specific lines):\n%s\n\n" +
            "CRITICAL: The message must ONLY be about the offer and feeling disappointed they haven't unlocked. NEVER mention robots, bots, automation, or meta replies.\n" +
            "Generate a SHORT emotional message that:\n" +
            "- Makes them feel guilty for not responding\n" +
            "- Creates FOMO or missing out\n" +
            "- Sounds genuinely disappointed\n" +
            "Respond with ONLY the message text.",
            offer.getLevel(),
            offer.getPrice(),
            conversationContext
        );
        
        try {
            return anthropicService.generateResponse(
                "You generate guilt-trap follow-up messages only. Output only the message text. Never mention robots, bots, or meta-replies.",
                analysisPrompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate guilt trap message", e);
            return "I made this just for you but okay... 💔";
        }
    }
}
