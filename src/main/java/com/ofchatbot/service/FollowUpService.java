package com.ofchatbot.service;

import com.ofchatbot.entity.*;
import com.ofchatbot.exception.CannotMessageUserException;
import com.ofchatbot.repository.PPVOfferRepository;
import com.ofchatbot.repository.FanRepository;
import com.ofchatbot.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    @Value("${bot.enabled:false}")
    private boolean botEnabled;

    // ── PPV follow-up (5 min after offer) ───────────────────────────────

    @Scheduled(fixedRate = 60000)
    public void processFollowUps() {
        if (!botEnabled) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);
        List<PPVOffer> offersNeedingFollowUp = ppvOfferRepository.findOffersNeedingFollowUp(fiveMinutesAgo);

        for (PPVOffer offer : offersNeedingFollowUp) {
            try {
                if (offer.getFollowUpCount() == 0 && offer.getSentAt().isBefore(fiveMinutesAgo)) {
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
            offer.setFollowUpCount(1);
            ppvOfferRepository.save(offer);
            log.warn("Cannot message user (chat {}). Marked offer {} follow-up done.", chatId, offer.getId());
            return;
        }

        offer.setFollowUpCount(1);
        ppvOfferRepository.save(offer);
        log.info("Sent first follow-up for offer {} to fan {}", offer.getId(), fan.getId());
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
                "You generate PPV follow-up messages only. Output only the message text. Never mention robots, bots, confusion, or meta-replies. Always write in ENGLISH only.",
                analysisPrompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate follow-up message", e);
            return "Still thinking about it? 😏";
        }
    }

    // ── Re-engagement (fan went quiet for 2-4 hours) ────────────────────

    @Scheduled(fixedRate = 1800000) // every 30 minutes
    public void processReengagement() {
        if (!botEnabled) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursAgo = now.minusHours(2);
        LocalDateTime twentyFourHoursAgo = now.minusHours(24);

        // Fans who were active in last 24h but stopped 2+ hours ago
        List<Fan> quietFans = fanRepository.findByLastUpdatedBetweenAndOnlyfansUserIdIsNotNull(twentyFourHoursAgo, twoHoursAgo);

        for (Fan fan : quietFans) {
            try {
                if (fan.getOnlyfansChatId() == null) continue;

                List<Message> recentMessages = messageService.getRecentMessages(fan.getContactId(), 10);
                if (recentMessages.isEmpty()) continue;

                Message lastMessage = recentMessages.get(recentMessages.size() - 1);
                // If we already sent something within 2 hours (e.g. a previous nudge), skip
                if (!lastMessage.isFromFan() && lastMessage.getTimestamp().isAfter(twoHoursAgo)) continue;

                if (lastMessage.isFromFan()) {
                    // Bot dropped the ball — fan spoke last and never got a reply.
                    // Acknowledge the missed message and respond to it, don't just nudge.
                    sendDroppedBallRecovery(fan, recentMessages);
                } else {
                    // Bot spoke last and fan went quiet — normal re-engagement nudge.
                    sendReengagementNudge(fan, recentMessages);
                }
            } catch (Exception e) {
                log.error("Failed re-engagement for fan {}", fan.getId(), e);
            }
        }
    }

    /**
     * Bot dropped the ball — fan sent a message and never got a reply.
     * Acknowledge the missed message and respond to what they actually said.
     */
    private void sendDroppedBallRecovery(Fan fan, List<Message> recentMessages) {
        String displayName = fan.getOnlyfansDisplayName();

        StringBuilder context = new StringBuilder();
        int start = Math.max(0, recentMessages.size() - 5);
        for (int i = start; i < recentMessages.size(); i++) {
            Message m = recentMessages.get(i);
            context.append(m.isFromFan() ? "Fan" : "You").append(": ").append(m.getContent()).append("\n");
        }

        String prompt = String.format(
            "You missed the fan's last message and are now replying late.\n\n" +
            "Fan name: %s\n" +
            "Recent conversation:\n%s\n\n" +
            "RULES:\n" +
            "- Briefly apologize for the late reply (\"sorry babe got caught up\", \"omg sorry I just saw this\")\n" +
            "- Then RESPOND to what the fan actually said in their last message\n" +
            "- Keep it SHORT (1-2 sentences)\n" +
            "- 1 emoji max\n" +
            "- If the fan was asking for content/pics, say you're sending it now\n" +
            "- NEVER mention robots, bots, or automation\n" +
            "- Respond with ONLY the message text.",
            displayName != null ? displayName : "babe",
            context.toString()
        );

        String recovery;
        try {
            recovery = anthropicService.generateResponse(
                "You are replying late to a fan's message. Apologize briefly and respond to what they said. Output only the message text. Always write in ENGLISH only.",
                prompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate dropped-ball recovery for fan {}", fan.getId(), e);
            recovery = "sorry babe I just saw this, give me one sec";
        }

        if (recovery == null || recovery.isBlank()) {
            recovery = "sorry babe I just saw this, give me one sec";
        }

        String chatId = fan.getOnlyfansChatId();
        try {
            onlyFansApiService.sendMessage(chatId, recovery);
        } catch (CannotMessageUserException e) {
            log.warn("Cannot message fan {} for dropped-ball recovery (blocked/restricted)", fan.getId());
            return;
        }

        messageService.saveMessage(fan.getCreatorId(), fan.getContactId(), "assistant", recovery, LocalDateTime.now().toString(), "onlyfans");
        log.info("Sent dropped-ball recovery to fan {} (chat {}): {}", fan.getId(), chatId, recovery);
    }

    private void sendReengagementNudge(Fan fan, List<Message> recentMessages) {
        String displayName = fan.getOnlyfansDisplayName();

        // Build short context from last few messages
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, recentMessages.size() - 5);
        for (int i = start; i < recentMessages.size(); i++) {
            Message m = recentMessages.get(i);
            context.append(m.isFromFan() ? "Fan" : "You").append(": ").append(m.getContent()).append("\n");
        }

        String prompt = String.format(
            "Generate a short re-engagement message. The fan stopped replying ~2-4 hours ago.\n\n" +
            "Fan name: %s\n" +
            "Recent conversation:\n%s\n\n" +
            "RULES:\n" +
            "- 1 short message (3-10 words max)\n" +
            "- Casual, slightly playful check-in\n" +
            "- Can use their name\n" +
            "- 1 emoji max, or none\n" +
            "- Vibe examples: \"babe?\", \"you disappeared on me haha\", \"hellooo\", \"where'd you go :)\"\n" +
            "- NEVER mention robots, bots, or automation\n" +
            "- Respond with ONLY the message text.",
            displayName != null ? displayName : "babe",
            context.toString()
        );

        String nudge;
        try {
            nudge = anthropicService.generateResponse(
                "You write short casual re-engagement messages. Output only the message text. Always write in ENGLISH only.",
                prompt,
                null
            );
        } catch (Exception e) {
            log.error("Failed to generate re-engagement for fan {}", fan.getId(), e);
            nudge = "heyy where'd you go :)";
        }

        if (nudge == null || nudge.isBlank()) {
            nudge = "babe?";
        }

        String chatId = fan.getOnlyfansChatId();
        try {
            onlyFansApiService.sendMessage(chatId, nudge);
        } catch (CannotMessageUserException e) {
            log.warn("Cannot message fan {} for re-engagement (blocked/restricted)", fan.getId());
            return;
        }

        // Save nudge as a bot message so we don't double-send on next cycle
        messageService.saveMessage(fan.getCreatorId(), fan.getContactId(), "assistant", nudge, LocalDateTime.now().toString(), "onlyfans");
        log.info("Sent re-engagement nudge to fan {} (chat {}): {}", fan.getId(), chatId, nudge);
    }

}
