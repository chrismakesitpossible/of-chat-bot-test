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
 * Content script service: manages escalating content offers (L1-L7) with topic-based selection.
 * The AI picks content that matches the conversation context while respecting explicitness gating.
 */
@Service
@Slf4j
public class ScriptService {

    public static final String SCRIPT_ID = "SO5";

    // L0 and L7 are text-only (no media) — prices only apply to L1-L6
    private static final double[] PRICES = { 0, 3, 6, 9, 12, 15, 20, 0 }; // L0–L7
    private static final boolean[] TEXT_ONLY = { true, false, false, false, false, false, false, true }; // L0 & L7 = text only

    private final FanScriptProgressRepository fanScriptProgressRepository;
    private final ContentVaultService contentVaultService;
    private final AnthropicService anthropicService;
    private final PPVService ppvService;
    private final OnlyFansApiService onlyFansApiService;

    @Value("${script.force-all-fans:false}")
    private boolean forceAllFans;

    private static final int OFFER_COOLDOWN_DAYS = 2;
    private static final int MAX_ATTEMPTS_PER_LEVEL = 2;

    /** Content descriptions for each level — the AI uses these to understand what the media shows. */
    private static final String[] LEVEL_DESCRIPTIONS = {
        "Rapport opener — lifestyle/gym selfie, fully clothed, no flirting",
        "Before gym stretching — workout clothes, confident, teasing angles",
        "More stretching — getting warmer, suggestive poses, still clothed",
        "Tank top pulled up — chest reveal, partial undress, gym mat",
        "Leggings removed — underwear only, lying on mat, soft explicit",
        "Fully nude — self-pleasure, fingering, spread on mat",
        "Using toy — most explicit content, top-tier",
        "After-care — intimate wind-down, no explicit, warm and personal"
    };

    public ScriptService(
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
        return fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID)
            .orElseGet(() -> {
                FanScriptProgress p = new FanScriptProgress();
                p.setFanId(fanId);
                p.setScriptId(SCRIPT_ID);
                p.setCurrentLevel(0);
                p.setStartedAt(LocalDateTime.now());
                p.setUpdatedAt(LocalDateTime.now());
                return fanScriptProgressRepository.save(p);
            });
    }

    /** True if fan is on script and has not completed it (default script for new fans; never run twice). */
    public boolean isOnScriptNotCompleted(Long fanId) {
        if (forceAllFans) {
            return true;
        }
        return fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID)
            .map(p -> p.getCompletedAt() == null)
            .orElse(true); // no row = new fan = default script
    }

    public double getPriceForLevel(int level) {
        if (level < 0 || level >= PRICES.length) return 0;
        return PRICES[level];
    }

    public List<VaultMedia> getMediaForLevel(String creatorId, int level, Long fanId) {
        return contentVaultService.getMediaForScriptLevel(creatorId, SCRIPT_ID, level, fanId);
    }

    private static final String ON_SCRIPT_RULES = """
        CRITICAL: This message is the caption for a content/PPV offer.
        - Do NOT react to or quote other parts of the conversation. Use the conversation only to match their energy.
        - Always write in ENGLISH only.
        - Output ONLY the message text — the caption that goes with the photo/video. Nothing else.
        - Match Bambii's voice: short, lowercase, direct, "haha" for laughs, sparing emojis.
        """;

    private static String getSystemPromptForLevel(int level) {
        String levelDesc = (level >= 0 && level < LEVEL_DESCRIPTIONS.length)
            ? LEVEL_DESCRIPTIONS[level] : "flirty content offer";

        switch (level) {
            case 0:
                return ON_SCRIPT_RULES + """
                    Level 0 (Rapport). Fully clothed gym/lifestyle selfie. No flirting, just vibes.
                    Content: %s
                    Example tone: "about to hit the gym.. is this too much to wear? haha"
                    Generate ONLY the message text. Short, casual, 0-1 emojis.
                    """.formatted(levelDesc);
            case 1:
                return ON_SCRIPT_RULES + """
                    Level 1 (Warm-Up). Stretching before gym, workout clothes, playful and confident.
                    Content: %s
                    Example: "stretching before gym.. people keep staring haha"
                    Generate ONLY the message text. Short, flirty, 0-1 emojis.
                    """.formatted(levelDesc);
            case 2:
                return ON_SCRIPT_RULES + """
                    Level 2 (Tease). More stretching, getting warmer, suggestive but still clothed. Tease with words.
                    Content: %s
                    Example: "are you getting hard watching this? because I can tell you're looking 😏"
                    Generate ONLY the PPV message text.
                    """.formatted(levelDesc);
            case 3:
                return ON_SCRIPT_RULES + """
                    Level 3 (Partial Reveal). Tank top pulled up, chest tease. The gym can wait.
                    Content: %s
                    Example: "the gym is going to have to wait.. I'm way too distracted for that"
                    Generate ONLY the PPV message text.
                    """.formatted(levelDesc);
            case 4:
                return ON_SCRIPT_RULES + """
                    Level 4 (Soft Explicit). Leggings removed, underwear only, lying on gym mat. Getting explicit with words.
                    Content: %s
                    Example: "I'm lying on this mat with nothing on but my underwear and I am losing it right now"
                    Generate ONLY the PPV message text.
                    """.formatted(levelDesc);
            case 5:
                return ON_SCRIPT_RULES + """
                    Level 5 (Full Explicit). Fully nude, spread, fingering. Peak solo. Tell him to stroke for you.
                    Content: %s
                    Example: "I'm completely spread on this mat fingering myself thinking about you.. don't you dare finish yet"
                    Generate ONLY the PPV message text.
                    """.formatted(levelDesc);
            case 6:
                return ON_SCRIPT_RULES + """
                    Level 6 (Top Content). Using toy, most explicit. Make him feel this is exclusive and the best thing ever.
                    Content: %s
                    Example: "this is the best thing I have ever sent and you are the only one getting it"
                    Generate ONLY the PPV message text.
                    """.formatted(levelDesc);
            case 7:
                return ON_SCRIPT_RULES + """
                    Level 7 (After-care). FREE. Warm, soft, no explicit. Intimate, personal. No selling. Pure warmth.
                    Content: %s
                    Example: "I don't even feel bad about not making it to the gym honestly.. that was such a good time haha"
                    Generate ONLY the message text.
                    """.formatted(levelDesc);
            default:
                return ON_SCRIPT_RULES + "Generate a short, flirty OnlyFans message. Generate ONLY the message text.";
        }
    }

    public String generateScriptMessage(int level, List<Message> recentMessages) {
        String systemPrompt = getSystemPromptForLevel(level);
        List<Message> latest = (recentMessages == null || recentMessages.isEmpty())
            ? List.of()
            : recentMessages.stream().skip(Math.max(0, recentMessages.size() - 15)).toList();
        String userPrompt = latest.isEmpty()
            ? "No recent messages. Generate the script message for this level."
            : "Recent conversation (match their energy, do NOT react to or quote other lines):\n"
                + latest.stream()
                    .map(m -> (m.isFromFan() ? "Fan" : "You") + ": " + m.getContent())
                    .collect(Collectors.joining("\n"))
                + "\n\nGenerate ONLY the script message for this level — the caption for the content offer.";
        return anthropicService.generateResponse(systemPrompt, userPrompt, null);
    }

    /**
     * Send PPV for the fan's current level. Call only when isOnScriptNotCompleted(fan.getId()).
     * Returns false if we sent the same level within cooldown (caller can fall back to main PPV).
     */
    public boolean sendScriptPPVOffer(Fan fan, String chatId, List<Message> recentMessages) {
        FanScriptProgress progress = getOrCreateProgress(fan.getId());
        int level = progress.getCurrentLevel();
        if (level < 0 || level > 7) {
            log.warn("Script level out of range for fan {}: {}", fan.getId(), level);
            return false;
        }

        // Max 2 attempts per level — if fan isn't buying, stop pushing (Issue #20)
        int attempts = progress.getAttemptsAtCurrentLevel() != null ? progress.getAttemptsAtCurrentLevel() : 0;
        if (attempts >= MAX_ATTEMPTS_PER_LEVEL) {
            log.info("L{} abandoned for fan {} after {} attempts — marking script completed", level, fan.getId(), attempts);
            progress.setCompletedAt(LocalDateTime.now());
            fanScriptProgressRepository.save(progress);
            return false;
        }

        // Avoid repeating the same offer within cooldown
        LocalDateTime lastSent = progress.getLastOfferSentAt();
        if (lastSent != null && ChronoUnit.DAYS.between(lastSent, LocalDateTime.now()) < OFFER_COOLDOWN_DAYS) {
            log.info("L{} offer for fan {} skipped (cooldown: sent {} days ago)", level, fan.getId(), ChronoUnit.DAYS.between(lastSent, LocalDateTime.now()));
            return false;
        }

        double price = getPriceForLevel(level);
        String message = generateScriptMessage(level, recentMessages);

        // L0 and L7 are text-only — no media, no PPV
        if (TEXT_ONLY[level]) {
            onlyFansApiService.sendMessage(chatId, message);
            progress.setLastOfferSentAt(LocalDateTime.now());
            progress.setAttemptsAtCurrentLevel(0);
            progress.setCurrentLevel(level + 1);
            fanScriptProgressRepository.save(progress);
            log.info("Sent text-only L{} to fan {} (no media)", level, fan.getId());
        } else {
            List<VaultMedia> media = getMediaForLevel(fan.getCreatorId(), level, fan.getId());
            if (media.isEmpty()) {
                log.warn("No media for level {} for fan {}", level, fan.getId());
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
                SCRIPT_ID,
                level
            );
            progress.setLastOfferSentAt(LocalDateTime.now());
            progress.setAttemptsAtCurrentLevel(attempts + 1);
            fanScriptProgressRepository.save(progress);
            log.info("Sent script L{} PPV to fan {} (${}, {} items)", level, fan.getId(), price, mediaIds.size());
        }
        return true;
    }

    /**
     * Advance script level after a purchase. If they bought L6, advance to 7, send L7 after-care, set completed_at.
     */
    public void onScriptPurchase(Long fanId, int purchasedLevel, String chatId, Creator creator) {
        FanScriptProgress progress = fanScriptProgressRepository.findByFanIdAndScriptId(fanId, SCRIPT_ID).orElse(null);
        if (progress == null || progress.getCompletedAt() != null) return;

        int newLevel = purchasedLevel + 1;
        progress.setCurrentLevel(newLevel);
        progress.setAttemptsAtCurrentLevel(0);
        fanScriptProgressRepository.save(progress);

        if (newLevel == 7) {
            progress.setCompletedAt(LocalDateTime.now());
            fanScriptProgressRepository.save(progress);
            sendL7AfterCare(chatId, creator);
            log.info("Script completed for fan {}; sent L7 after-care", fanId);
        }
    }

    /** Send L7 after-care as a free (non-PPV) message. */
    public void sendL7AfterCare(String chatId, Creator creator) {
        String message = generateScriptMessage(7, List.of());
        onlyFansApiService.sendMessage(chatId, message, null, creator.getCreatorId());
    }
}
