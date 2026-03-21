package com.ofchatbot.service;

import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.FanScriptProgress;
import com.ofchatbot.entity.Message;
import com.ofchatbot.entity.VaultMedia;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.repository.FanScriptProgressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shower script (S01): default script only. Never run twice per fan (completed_at set after L7).
 */
@Service
@Slf4j
public class ShowerScriptService {

    public static final String SCRIPT_ID_SHOWER = "S01";

    // L0 and L7 are text-only (no media) — prices only apply to L1-L6
    private static final double[] PRICES = { 0, 3, 6, 9, 12, 15, 20, 0 }; // L0–L7
    private static final boolean[] TEXT_ONLY = { true, false, false, false, false, false, false, true }; // L0 & L7 = text only

    private final FanScriptProgressRepository fanScriptProgressRepository;
    private final ContentVaultService contentVaultService;
    private final AnthropicService anthropicService;
    private final PPVService ppvService;
    private final OnlyFansApiService onlyFansApiService;

    @Value("${shower.script.force-all-fans:false}")
    private boolean forceAllFansOnShower;

    /** Don't send the same shower level again within this many days (avoids repetitive "just got out of the shower" spam). */
    private static final int SHOWER_OFFER_COOLDOWN_DAYS = 2;

    /** Max attempts at any single level before abandoning the shower script for this fan (Issue #20). */
    private static final int MAX_ATTEMPTS_PER_LEVEL = 2;

    public ShowerScriptService(
            FanScriptProgressRepository fanScriptProgressRepository,
            ContentVaultService contentVaultService,
            AnthropicService anthropicService,
            @Lazy PPVService ppvService,
            OnlyFansApiService onlyFansApiService) {
        this.fanScriptProgressRepository = fanScriptProgressRepository;
        this.contentVaultService = contentVaultService;
        this.anthropicService = anthropicService;
        this.ppvService = ppvService;
        this.onlyFansApiService = onlyFansApiService;
    }

    public FanScriptProgress getOrCreateProgress(Long fanId) {
        return fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID_SHOWER)
            .orElseGet(() -> {
                FanScriptProgress p = new FanScriptProgress();
                p.setFanId(fanId);
                p.setScriptId(SCRIPT_ID_SHOWER);
                p.setCurrentLevel(0);
                p.setStartedAt(LocalDateTime.now());
                p.setUpdatedAt(LocalDateTime.now());
                return fanScriptProgressRepository.save(p);
            });
    }

    /** True if fan is on Shower script and has not completed it (default script for new fans; never run script twice). */
    public boolean isOnShowerNotCompleted(Long fanId) {
        if (forceAllFansOnShower) {
            return true; // testing: every fan gets Shower
        }
        return fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID_SHOWER)
            .map(p -> p.getCompletedAt() == null)
            .orElse(true); // no row = new fan = default Shower script
    }

    public double getPriceForLevel(int level) {
        if (level < 0 || level >= PRICES.length) return 0;
        return PRICES[level];
    }

    public List<VaultMedia> getMediaForLevel(String creatorId, int level, Long fanId) {
        return contentVaultService.getMediaForScriptLevel(creatorId, SCRIPT_ID_SHOWER, level, fanId);
    }

    private static final String ON_SCRIPT_RULES = """
        CRITICAL: This message is the caption for a content/PPV offer. It must ONLY be about the shower script and this level.
        - NEVER say things like: "what are you talking about", "are you confused", "trying to tell me how to text", "what's happening", "wait you're confusing me", or any meta/confused reply to the chat.
        - Do NOT react to or quote other parts of the conversation. Use the conversation only to match their energy and what they asked for (e.g. they want more → be flirty and deliver the offer).
        - Always write in ENGLISH only. Never use the fan's language (e.g. Tagalog, Spanish).
        - Output ONLY the script message for this level — the caption that goes with the photo/video. Nothing else.
        """;

    private static String getSystemPromptForLevel(int level) {
        switch (level) {
            case 0:
                return ON_SCRIPT_RULES + """
                    You are writing a single message for the Shower script, Level 0 (Rapport). Outside session.
                    Send one lifestyle photo only. Post-shower energy — towel wrapped, fresh skin, warm bathroom light. Fully covered. No flirting, no heat.
                    Example tone: "Just got out of the shower and my skin feels so soft right now 🚿✨ honestly love that post-shower feeling so much... hope your day is treating you good baby 🖤"
                    Generate ONLY the message text. Stay on script but you may vary wording slightly. One short message, 1-2 emojis max.
                    """;
            case 1:
                return ON_SCRIPT_RULES + """
                    You are writing a single message for the Shower script, Level 1 (Warm-Up). Session opens.
                    One flirty photo. Towel on, fully covered. Playful, confident. Hint at what could happen without promising.
                    Example: "I just got out of the shower and honestly couldn't be bothered getting dressed yet 😏🚿 is that bad? what are you up to right now...? 👀"
                    Generate ONLY the message text. Stay on script but you may vary wording slightly. Short, flirty, 1-2 emojis.
                    """;
            case 2:
                return ON_SCRIPT_RULES + """
                    Shower script Level 2 (Tease). Bundle: 4-5 photos, optional short video. Towel loosening, steam, nothing explicit. Do the work with words — content stays clean, language doesn't.
                    Example: "The towel keeps almost slipping off me and I may have let it drop a little just now 🙈💦 the water is still dripping down my skin... are you getting hard thinking about it baby? Because I haven't even started yet 😈🔥"
                    Generate ONLY the PPV message text. Stay on script but you may vary wording slightly.
                    """;
            case 3:
                return ON_SCRIPT_RULES + """
                    Shower script Level 3 (Undress + First Touch). Nudity wall. Bundle: 4-5 photos + 2-3 min video. Towel at waist, hands on yourself, peak zones glimpsed only. Language describes more than shown.
                    Example: "The towel just fell and I didn't pick it up... my skin is still warm from the water. I'm running my hands over myself right now thinking about what you'd do if you were here with me..."
                    Generate ONLY the PPV message text. Stay on script but you may vary wording slightly.
                    """;
            case 4:
                return ON_SCRIPT_RULES + """
                    Shower script Level 4 (Soft Explicit). First full reveal. 5-6 photos, 3-5 min video. Fully topless, touching more visibly. Go explicit with words — describe every sensation. Tell him how wet you are.
                    Example: "I'm rubbing oil all over my entire body right now and I genuinely cannot stop touching myself baby 🤤🛁 I'm already absolutely dripping... tell me you're hard"
                    Generate ONLY the PPV message text. Stay on script but you may vary wording slightly.
                    """;
            case 5:
                return ON_SCRIPT_RULES + """
                    Shower script Level 5 (Full Explicit). Peak solo. 5-6 photos + 5-8 min video. Full nudity, completely explicit. JOI — tell him to stroke himself for you. Then tell him NOT to finish yet — you have one more thing (pitch L6).
                    Example: "I literally cannot stop touching myself right now — I'm so fucking turned on 🥵💦 I want you to stroke yourself for me baby, do it right now. But don't you dare come yet — I have one more thing for you first. Hold on for me 😈🔥"
                    Generate ONLY the PPV message text. Stay on script but you may vary wording slightly.
                    """;
            case 6:
                return ON_SCRIPT_RULES + """
                    Shower script Level 6 (Top content). Different act from L5 — same shower, new scene. 5-6 photos + 5+ min video. Build anticipation. Make him feel he's getting something private. Session ceiling.
                    Example: "Okay I actually lied... 😈🔥 this is the real last one and it is genuinely the best thing I've ever filmed. I've never sent this to anyone before you. Finish with me right now baby 🥵💦😮‍💨"
                    Generate ONLY the PPV message text. Stay on script but you may vary wording slightly.
                    """;
            case 7:
                return ON_SCRIPT_RULES + """
                    Shower script Level 7 (After-care). FREE. Warm, soft, no explicit. Intimate, personal, low energy. Make him feel seen and connected. No agenda, no ask. Pure warmth. Re-hook for next time.
                    Example: "That was so incredibly good baby... I'm genuinely still thinking about it 😮‍💨🖤 you always know exactly how to make me feel like that. Get some rest tonight — I'll be thinking about you 🌙✨"
                    Generate ONLY the message text. Stay on script but you may vary wording slightly.
                    """;
            default:
                return ON_SCRIPT_RULES + "Generate a short, flirty OnlyFans message. One or two sentences. Generate ONLY the message text.";
        }
    }

    public String generateShowerMessage(int level, List<Message> recentMessages) {
        String systemPrompt = getSystemPromptForLevel(level);
        // Use the most recent messages (list is oldest-first), not the first N which would be the oldest
        List<Message> latest = (recentMessages == null || recentMessages.isEmpty())
            ? List.of()
            : recentMessages.stream().skip(Math.max(0, recentMessages.size() - 15)).toList();
        String userPrompt = latest.isEmpty()
            ? "No recent messages. Generate the script message for this level."
            : "Recent conversation (use only to match their energy and what they asked for, e.g. they want more content → be flirty and deliver; do NOT react to or quote other lines):\n"
                + latest.stream()
                    .map(m -> (m.isFromFan() ? "Fan" : "You") + ": " + m.getContent())
                    .collect(Collectors.joining("\n"))
                + "\n\nGenerate ONLY the script message for this level — the caption for the content offer. No meta or confused replies.";
        return anthropicService.generateResponse(systemPrompt, userPrompt, null);
    }

    /**
     * Send Shower PPV for the fan's current level. Call only when isOnShowerNotCompleted(fan.getId()).
     * Returns false if we sent the same level within cooldown (caller can fall back to main PPV).
     */
    public boolean sendShowerPPVOffer(Fan fan, String chatId, List<Message> recentMessages) {
        FanScriptProgress progress = getOrCreateProgress(fan.getId());
        int level = progress.getCurrentLevel();
        if (level < 0 || level > 7) {
            log.warn("Shower script level out of range for fan {}: {}", fan.getId(), level);
            return false;
        }

        // Max 2 attempts per level — if fan isn't buying, stop pushing (Issue #20)
        int attempts = progress.getAttemptsAtCurrentLevel() != null ? progress.getAttemptsAtCurrentLevel() : 0;
        if (attempts >= MAX_ATTEMPTS_PER_LEVEL) {
            log.info("Shower L{} abandoned for fan {} after {} attempts — marking script completed", level, fan.getId(), attempts);
            progress.setCompletedAt(LocalDateTime.now());
            fanScriptProgressRepository.save(progress);
            return false;
        }

        // Avoid repeating the same shower offer within cooldown (stops "just got out of the shower" spam).
        LocalDateTime lastSent = progress.getLastShowerOfferSentAt();
        if (lastSent != null && ChronoUnit.DAYS.between(lastSent, LocalDateTime.now()) < SHOWER_OFFER_COOLDOWN_DAYS) {
            log.info("Shower L{} offer for fan {} skipped (cooldown: sent {} days ago)", level, fan.getId(), ChronoUnit.DAYS.between(lastSent, LocalDateTime.now()));
            return false;
        }

        double price = getPriceForLevel(level);
        String message = generateShowerMessage(level, recentMessages);

        // L0 and L7 are text-only — no media, no PPV (Issue #1.2)
        if (TEXT_ONLY[level]) {
            onlyFansApiService.sendMessage(chatId, message);
            progress.setLastShowerOfferSentAt(LocalDateTime.now());
            progress.setAttemptsAtCurrentLevel(0); // reset on advance
            progress.setCurrentLevel(level + 1);
            fanScriptProgressRepository.save(progress);
            log.info("Sent text-only Shower L{} to fan {} (no media)", level, fan.getId());
        } else {
            List<VaultMedia> media = getMediaForLevel(fan.getCreatorId(), level, fan.getId());
            if (media.isEmpty()) {
                log.warn("No media for Shower level {} for fan {}", level, fan.getId());
                return false;
            }
            List<String> mediaIds = media.stream().map(VaultMedia::getMediaId).toList();

            ppvService.sendAndTrackPPV(
                chatId,
                fan.getId(),
                message,
                price,
                mediaIds,
                "SOLO",
                level,
                SCRIPT_ID_SHOWER,
                level
            );
            progress.setLastShowerOfferSentAt(LocalDateTime.now());
            progress.setAttemptsAtCurrentLevel(attempts + 1);
            fanScriptProgressRepository.save(progress);
            log.info("Sent Shower script L{} PPV to fan {} (${}, {} items)", level, fan.getId(), price, mediaIds.size());
        }
        return true;
    }

    /**
     * Advance script level after a purchase. If they bought L6, advance to 7, send L7 after-care, set completed_at.
     */
    public void onShowerPurchase(Long fanId, int purchasedLevel, String chatId, Creator creator) {
        FanScriptProgress progress = fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID_SHOWER).orElse(null);
        if (progress == null || progress.getCompletedAt() != null) return;

        int newLevel = purchasedLevel + 1;
        progress.setCurrentLevel(newLevel);
        progress.setAttemptsAtCurrentLevel(0); // reset on purchase
        fanScriptProgressRepository.save(progress);

        if (newLevel == 7) {
            progress.setCompletedAt(LocalDateTime.now());
            fanScriptProgressRepository.save(progress);
            sendL7AfterCare(chatId, creator);
            log.info("Shower script completed for fan {}; sent L7 after-care", fanId);
        }
    }

    /** Send L7 after-care as a free (non-PPV) message. */
    public void sendL7AfterCare(String chatId, Creator creator) {
        String message = generateShowerMessage(7, List.of());
        onlyFansApiService.sendMessage(chatId, message, null, creator.getCreatorId());
    }
}
