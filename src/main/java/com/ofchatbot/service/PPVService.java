package com.ofchatbot.service;

import com.ofchatbot.dto.OnlyFansPPVMessageRequest;
import com.ofchatbot.entity.*;
import com.ofchatbot.repository.PPVOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PPVService {

    /** Body / appearance tags (oily, wet, lingerie, etc.) should be prioritized when matching content. */
    private static final Set<String> BODY_APPEARANCE_TAGS = Set.of(
        "oily", "wet", "nude", "lingerie", "stockings", "barefoot", "socks", "robe"
    );

    /** Location / setting tags (shower, hotel, kitchen, etc.) to match fan "vibe" preferences. */
    private static final Set<String> LOCATION_SETTING_TAGS = Set.of(
        "hotel", "car", "shower", "kitchen", "bedroom", "couch", "outdoors", "vacation"
    );

    /** Energy / style tags (dominant, rough, GFE, etc.) to match fan personality or stated kink. */
    private static final Set<String> ENERGY_STYLE_TAGS = Set.of(
        "dominant", "submissive", "rough", "romantic", "slow", "intense", "shy", "gfe", "humiliation", "roleplay"
    );

    /** Duration preference tags: short (<10), medium (10–20), long (20+). */
    private static final Set<String> DURATION_TAGS = Set.of(
        "short", "medium", "long"
    );

    /**
     * Acts / theme tags (JOI, BJ, doggy, etc.) are the most specific/explicit.
     * Spec: "Only surface after trust is built. Do not lead with this."
     * We include them in content filtering only once trust is established.
     */
    private static final Set<String> ACTS_THEME_TAGS = Set.of(
        "joi", "bj", "doggy", "missionary", "cowgirl", "squirt",
        "creampie", "facial", "swallow", "fingering", "dildo", "vibrator", "feet"
    );

    private final PPVOfferRepository ppvOfferRepository;
    private final ContentVaultService contentVaultService;
    private final AnthropicService anthropicService;
    private final RestTemplate restTemplate;
    private final ErrorLogService errorLogService;
    private final FanInterestService fanInterestService;
    private final CustomRequestService customRequestService;
    private final MessageService messageService;
    private final PeakInterestDetectionService peakInterestDetectionService;
    private final PricingLadderService pricingLadderService;
    private final PPVPurchaseService ppvPurchaseService;
    private final OnlyFansApiService onlyFansApiService;

    @Value("${onlyfans.api.key}")
    private String apiKey;

    @Value("${onlyfans.api.base-url}")
    private String baseUrl;

    @Value("${onlyfans.account.id}")
    private String accountId;

    @Value("${pricing.session-hours:2}")
    private int sessionHours = 2;

    /** Step 1: Ladder pricing. First offer = free teaser. Then wallet test ($9.95 or $29.95–$49.95 if Tier1/active). Then ladder by spend/engagement. */
    public void sendPPVOffer(Fan fan, ConversationState state, Conversation conversation, String chatId, List<Message> recentMessages) {
        sendPPVOffer(fan, state, conversation, chatId, recentMessages, false, false);
    }

    /**
     * @param hasSpecificRequest true when fan asked for specific/niche content (price $49.95–$99.95).
     * @param reengagingAfterCold true when fan was inactive for several days and is now back ($9.95–$19.95 re-warm).
     * @param fanMentionedPriceHint if fan said a price (e.g. "send for $60"), use at least this tier; null to ignore.
     */
    public void sendPPVOffer(Fan fan, ConversationState state, Conversation conversation, String chatId, List<Message> recentMessages, boolean hasSpecificRequest, boolean reengagingAfterCold) {
        sendPPVOffer(fan, state, conversation, chatId, recentMessages, hasSpecificRequest, reengagingAfterCold, null);
    }

    public void sendPPVOffer(Fan fan, ConversationState state, Conversation conversation, String chatId, List<Message> recentMessages, boolean hasSpecificRequest, boolean reengagingAfterCold, Double fanMentionedPriceHint) {
        long offerCount = ppvOfferRepository.countByFanId(fan.getId());
        boolean hasPurchasedBefore = contentVaultService.hasFanMadeAnyPurchase(fan.getId());

        if (offerCount == 0) {
            sendFreeTeaser(fan, state, chatId, recentMessages);
            return;
        }

        // Avoid spamming the same priced PPV over and over when previous ones weren't purchased.
        List<PPVOffer> previousUnpurchased = ppvOfferRepository.findByFanIdAndPurchasedFalse(fan.getId());

        long purchasesInSession = ppvPurchaseService.countPurchasesInLastHours(fan.getId(), sessionHours);
        double rawStartPrice = pricingLadderService.determineStartPrice(
            fan,
            true,
            state.getTotalSpent(),
            hasSpecificRequest,
            (int) purchasesInSession,
            reengagingAfterCold
        );
        if (fanMentionedPriceHint != null && fanMentionedPriceHint > 0) {
            double hinted = pricingLadderService.roundToLadder(fanMentionedPriceHint);
            if (hinted > rawStartPrice) {
                rawStartPrice = hinted;
                log.info("Using fan-mentioned price hint ${} (rounded to ladder) for fan {}", hinted, fan.getId());
            }
        }
        final double effectiveRawStartPrice = rawStartPrice;

        // If we've already sent several recent offers at this same price that were not purchased,
        // back off instead of sending yet another identical PPV.
        long samePriceRecentOffers = previousUnpurchased.stream()
            .filter(o -> o.getPrice() != null && Math.abs(o.getPrice() - effectiveRawStartPrice) < 0.01)
            .count();
        if (samePriceRecentOffers >= 3) {
            log.info("Skipping PPV offer for fan {} at ${} to avoid repetitive spam ({} similar unpaid offers).",
                fan.getId(), effectiveRawStartPrice, samePriceRecentOffers);
            return;
        }

        ContentCategory category = chooseCategoryForFan(
            fan,
            effectiveRawStartPrice,
            hasSpecificRequest,
            purchasesInSession,
            hasPurchasedBefore
        );
        double startPrice = pricingLadderService.roundToCategoryLadder(effectiveRawStartPrice, category);
        // Peak interest detection kept for analytics only — no price bumps (Issue #5.2)

        // Extract/update fan interests from the conversation so we can both personalize content selection
        // (e.g. "shower" -> SHOWER SCRIPT tags) and pass interests into the PPV pitch prompt.
        fanInterestService.extractAndTrackInterests(fan.getId(), conversation.getMessages());
        List<String> allInterests = fanInterestService.getTopInterestCategories(fan.getId(), 10);

        // Determine whether we have enough trust to surface explicit Acts/Theme interests.
        boolean trustBuilt = hasPurchasedBefore
            || (state.getIntensityLevel() != null && state.getIntensityLevel() >= 6);

        // PRIORITY:
        // 1) Body / Appearance tags first (oily, wet, lingerie, feet look, etc.)
        // 2) Location / Setting (hotel, shower, kitchen, etc.)
        // 3) Acts / Theme (JOI, BJ, doggy, etc.) ONLY after trust is built
        // 4) Everything else
        List<String> prioritizedInterests = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1) Body / Appearance
        for (String interest : allInterests) {
            if (interest != null && BODY_APPEARANCE_TAGS.contains(interest.toLowerCase())) {
                if (seen.add(interest)) {
                    prioritizedInterests.add(interest);
                }
            }
        }

        // 2) Location / Setting
        for (String interest : allInterests) {
            if (interest != null && LOCATION_SETTING_TAGS.contains(interest.toLowerCase())) {
                if (seen.add(interest)) {
                    prioritizedInterests.add(interest);
                }
            }
        }

        // 3) Energy / Style (match to fan personality or stated kink)
        for (String interest : allInterests) {
            if (interest != null && ENERGY_STYLE_TAGS.contains(interest.toLowerCase())) {
                if (seen.add(interest)) {
                    prioritizedInterests.add(interest);
                }
            }
        }

        // 4) Duration (short/medium/long) – for budget-conscious fans or those who care about length
        for (String interest : allInterests) {
            if (interest != null && DURATION_TAGS.contains(interest.toLowerCase())) {
                if (seen.add(interest)) {
                    prioritizedInterests.add(interest);
                }
            }
        }

        // 5) Acts / Theme (only if trust built)
        if (trustBuilt) {
            for (String interest : allInterests) {
                if (interest != null && ACTS_THEME_TAGS.contains(interest.toLowerCase())) {
                    if (seen.add(interest)) {
                        prioritizedInterests.add(interest);
                    }
                }
            }
        }

        // 6) Everything else
        for (String interest : allInterests) {
            if (interest != null && seen.add(interest)) {
                prioritizedInterests.add(interest);
            }
        }
        List<String> topInterests = prioritizedInterests.size() > 3
            ? prioritizedInterests.subList(0, 3)
            : prioritizedInterests;

        // Never reuse media already sent to this fan in any PPV offer — keep main PPV random and non-repeating.
        Set<String> alreadySentMediaIds = getMediaIdsAlreadySentToFan(fan.getId());

        List<VaultMedia> mediaBundle = contentVaultService.getMediaForPriceTierPersonalized(
            fan.getId(), category, startPrice, topInterests, alreadySentMediaIds
        );
        if (mediaBundle.isEmpty()) {
            startPrice = PricingLadderService.P_9_95;
            mediaBundle = contentVaultService.getMediaForPriceTierPersonalized(
                fan.getId(), category, startPrice, topInterests, alreadySentMediaIds
            );
        }
        if (mediaBundle.isEmpty()) {
            log.warn("No media for fan {} at any price tier", fan.getId());
            return;
        }

        // Compute total duration in minutes for categories where duration matters for the pitch
        // (Full Sextapes and Bundles), so we can "label duration clearly" in the offer.
        Integer totalMinutes = null;
        if (category == ContentCategory.FULL_SEXTAPE || category == ContentCategory.BUNDLE) {
            int totalSeconds = mediaBundle.stream()
                .map(VaultMedia::getDuration)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
            if (totalSeconds > 0) {
                totalMinutes = Math.max(1, Math.round(totalSeconds / 60.0f));
            }
        }

        java.util.List<String> personalizationBits = new java.util.ArrayList<>();
        if (!topInterests.isEmpty()) {
            personalizationBits.add("Fan interests: " + String.join(", ", topInterests));
        }
        if (totalMinutes != null) {
            if (category == ContentCategory.FULL_SEXTAPE) {
                personalizationBits.add("Full tape duration: ~" + totalMinutes + " min");
            } else if (category == ContentCategory.BUNDLE) {
                personalizationBits.add("Bundle duration: ~" + totalMinutes + " min total");
            }
        }
        String personalizationContext = personalizationBits.isEmpty()
            ? ""
            : String.join(" | ", personalizationBits);

        String pitchMessage = generatePPVPitchByPrice(startPrice, state, recentMessages, mediaBundle.size(), personalizationContext);
        List<String> mediaIds = mediaBundle.stream().map(VaultMedia::getMediaId).toList();
        sendPPVMessage(chatId, pitchMessage, startPrice, mediaIds, null);
        trackOffer(fan.getId(), pricingLadderService.priceToTierIndex(startPrice), category.name(), startPrice, String.join(",", mediaIds));

        log.info("Sent PPV to fan {}: ${}, {} items ({})", fan.getId(), startPrice, mediaBundle.size(),
            hasPurchasedBefore ? "returning" : "wallet test");
    }

    /** All media IDs ever sent to this fan in any PPV offer (purchased or not). Used so we never repeat the same photo/video. */
    private Set<String> getMediaIdsAlreadySentToFan(Long fanId) {
        List<PPVOffer> offers = ppvOfferRepository.findAllByFanId(fanId);
        Set<String> sent = new HashSet<>();
        for (PPVOffer o : offers) {
            if (o.getMediaIds() != null && !o.getMediaIds().isBlank()) {
                for (String id : o.getMediaIds().split(",")) {
                    String t = id.trim();
                    if (!t.isEmpty()) sent.add(t);
                }
            }
        }
        return sent;
    }

    private void sendFreeTeaser(Fan fan, ConversationState state, String chatId, List<Message> recentMessages) {
        // Text-only tease — never send real media for free (Issue #1.1)
        String teaserMessage = generatePPVPitchByPrice(PricingLadderService.FREE, state, recentMessages, 0, "");
        onlyFansApiService.sendMessage(chatId, teaserMessage);
        trackOffer(fan.getId(), 0, ContentCategory.TEASER.name(), 0.0, null);
        log.info("Sent text-only teaser to fan {} (no free media)", fan.getId());
    }

    private String generatePPVPitchByPrice(double price, ConversationState state, List<Message> recentMessages, int itemCount, String personalizationContext) {
        String systemPrompt = buildPPVPitchSystemPromptByPrice(price, state, itemCount, personalizationContext);
        String userPrompt = buildPPVPitchUserPrompt(recentMessages);
        return anthropicService.generateResponse(systemPrompt, userPrompt, null);
    }

    private String buildPPVPitchSystemPromptByPrice(double price, ConversationState state, int itemCount, String personalizationContext) {
        String priceDesc = price <= 0 ? "FREE teaser" : String.format("$%.2f", price);
        String itemDesc = itemCount == 1 ? "1 item" : itemCount + " items";
        String personalizationNote = personalizationContext.isEmpty() ? "" :
            "\nPERSONALIZATION: " + personalizationContext + " - Subtly reference in your pitch.";
        return String.format("""
            You are crafting a PPV (pay-per-view) content offer message.
            Price: %s. Bundle: %s. Fan messages: %d. Phase: %s.%s
            CRITICAL: Keep message SHORT (1-2 sentences), natural and flirty, 1-2 emojis max. Generate ONLY the message text.
            """, priceDesc, itemDesc, state.getMessageCount(), state.getCurrentPhase(), personalizationNote);
    }

    private String buildPPVPitchUserPrompt(List<Message> recentMessages) {
        List<Message> sorted = recentMessages.stream()
            .sorted((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()))
            .limit(5)
            .toList();

        StringBuilder context = new StringBuilder("Recent conversation:\n");
        for (Message msg : sorted) {
            String sender = msg.isFromFan() ? "Fan" : "You";
            context.append(sender).append(": ").append(msg.getContent()).append("\n");
        }

        context.append("\nGenerate a natural PPV offer message that flows from this conversation.");

        return context.toString();
    }

    private void sendPPVMessage(String chatId, String text, Double price, List<String> mediaIds, String replyToMessageId) {
        String url = String.format("%s/%s/chats/%s/messages", baseUrl, accountId, chatId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Integer apiPrice = price == null ? null : (int) Math.round(price);
        // GUARD: Never send media for free — minimum $3 (OF platform minimum)
        if (mediaIds != null && !mediaIds.isEmpty()) {
            if (apiPrice == null || apiPrice < 3) {
                log.error("Blocked $0 media send to chat {}. Forcing minimum $3.", chatId);
                apiPrice = 3;
            }
        }
        if (apiPrice != null && apiPrice > 0 && apiPrice < 3) {
            apiPrice = 3;
        }
        // Send both mediaFiles and media_ids for compatibility with provider variations.
        OnlyFansPPVMessageRequest request = new OnlyFansPPVMessageRequest(
            text,
            apiPrice,
            mediaIds,
            mediaIds,
            null,
            replyToMessageId
        );
        HttpEntity<OnlyFansPPVMessageRequest> httpRequest = new HttpEntity<>(request, headers);

        try {
            restTemplate.exchange(
                url, HttpMethod.POST, httpRequest, String.class
            );
            log.info("Successfully sent PPV message to chat: {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send PPV message to chat: {}", chatId, e);
            errorLogService.logError(
                "PPV_SEND_FAILED",
                "Failed to send PPV message to chat: " + chatId,
                e,
                "Price: " + price + ", Media IDs: " + mediaIds
            );
            throw new RuntimeException("Failed to send PPV message", e);
        }
    }

    public void trackOffer(Long fanId, Integer level, Double price, String mediaIds) {
        trackOffer(fanId, level, null, price, mediaIds);
    }

    public void trackOffer(Long fanId, Integer level, String contentCategory, Double price, String mediaIds) {
        trackOffer(fanId, level, contentCategory, price, mediaIds, null, null);
    }

    public void trackOffer(Long fanId, Integer level, String contentCategory, Double price, String mediaIds, String scriptId, Integer scriptLevel) {
        PPVOffer offer = new PPVOffer();
        offer.setFanId(fanId);
        offer.setLevel(level != null ? level : 0);
        offer.setContentCategory(contentCategory);
        offer.setPrice(price != null ? price : 0.0);
        offer.setMediaIds(mediaIds != null ? mediaIds : "");
        offer.setPurchased(false);
        offer.setFollowUpCount(0);
        offer.setScriptId(scriptId);
        offer.setScriptLevel(scriptLevel);
        offer = ppvOfferRepository.save(offer);
        // So only this new PPV gets the one follow-up; old unpurchased offers for this fan won't get a follow-up.
        ppvOfferRepository.markOtherUnpurchasedFollowUpDone(fanId, offer.getId());
    }

    /** Send PPV and track offer (for script-based offers). Runs in a transaction so the follow-up mark runs when called from the scheduler thread. */
    @Transactional
    public void sendAndTrackPPV(String chatId, Long fanId, String text, Double price, List<String> mediaIds, String contentCategory, int levelIndex, String scriptId, Integer scriptLevel) {
        sendPPVMessage(chatId, text, price, mediaIds, null);
        trackOffer(fanId, levelIndex, contentCategory, price, mediaIds != null ? String.join(",", mediaIds) : "", scriptId, scriptLevel);
    }

    /**
     * Choose which content category (Solo, Couple, Bundles, Full Sextape, etc.) to use for this fan and offer.
     *
     * High-level rules from OF Vault v2.0:
     * - FULL_SEXTAPE (🟡): Best upsell after teaser/bundle; use at high price or 2+ buys in a session.
     * - BUNDLE (🔴 Short/Longer/Theme):
     *   - Short Trim: for silent/non-chatty, low-trust fans (no wallet test, low engagement).
     *   - Longer Trim: natural escalation after a previous Short Trim bundle purchase.
     *   - Theme Bundle: implicit via interest-based selection over BUNDLE vaults.
     * - SOLO / COUPLE: default scripts, with fan interest deciding between them.
     */
    private ContentCategory chooseCategoryForFan(Fan fan, double startPrice, boolean hasSpecificRequest, long purchasesInSession, boolean hasPurchasedBefore) {
        // Existing stored interests (lingerie, feet, solo, couple, etc.)
        List<String> topInterests = fanInterestService.getTopInterestCategories(fan.getId(), 5);
        boolean likesCouple = topInterests.stream().anyMatch(i -> "couple".equalsIgnoreCase(i));
        boolean likesSolo = topInterests.stream().anyMatch(i -> "solo".equalsIgnoreCase(i));
        boolean active = pricingLadderService.isActiveReplier(fan);
        boolean passedWalletTest = Boolean.TRUE.equals(fan.getPassedWalletTest());

        // High-momentum upsell: after 2+ buys in a session or high starting price, offer full tape (colour code 🟡).
        if (startPrice >= PricingLadderService.P_99_95 || purchasesInSession >= 2) {
            return ContentCategory.FULL_SEXTAPE;
        }

        // Longer Trim (🔴) after a Short Trim bundle purchase:
        // If the last purchased offer was a BUNDLE at lower tier (~$19.95/$29.95),
        // and the ladder is now stepping up into the $29.95–$49.95 zone, keep using BUNDLE as escalation.
        PPVOffer lastPurchase = findLastPurchasedOffer(fan.getId());
        if (lastPurchase != null && lastPurchase.getContentCategory() != null
            && ContentCategory.BUNDLE.name().equalsIgnoreCase(lastPurchase.getContentCategory())) {
            double lastPrice = lastPurchase.getPrice() != null ? lastPurchase.getPrice() : 0.0;
            if (lastPrice <= PricingLadderService.P_29_95
                && startPrice >= PricingLadderService.P_29_95
                && startPrice <= PricingLadderService.P_49_95) {
                return ContentCategory.BUNDLE;
            }
        }

        // Short Trim (🔴) for silent / low-engagement fans:
        // - Not active replier
        // - No purchases yet
        // - No specific/niche request (just general content interest)
        // - Has not yet passed wallet test
        if (!active && !hasPurchasedBefore && !hasSpecificRequest && !passedWalletTest) {
            return ContentCategory.BUNDLE;
        }

        // If fan clearly prefers couple content, use Couple scripts (colour code 🟢).
        if (likesCouple && !likesSolo) {
            return ContentCategory.COUPLE;
        }

        // Specific/niche request + couple interest: also lean into Couple.
        if (hasSpecificRequest && likesCouple) {
            return ContentCategory.COUPLE;
        }

        // Default: Solo scripts (colour code 🟠).
        return ContentCategory.SOLO;
    }

    private PPVOffer findLastPurchasedOffer(Long fanId) {
        List<PPVOffer> purchased = ppvOfferRepository.findByFanIdAndPurchasedTrue(fanId);
        if (purchased == null || purchased.isEmpty()) {
            return null;
        }
        return purchased.stream()
            .filter(o -> o.getPurchasedAt() != null)
            .max(Comparator.comparing(PPVOffer::getPurchasedAt))
            .orElse(null);
    }
}
