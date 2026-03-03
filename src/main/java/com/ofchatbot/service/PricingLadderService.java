package com.ofchatbot.service;

import com.ofchatbot.entity.ContentCategory;
import com.ofchatbot.entity.Fan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Step 1: Colour code pricing. All prices end in .95.
 * Per-category tiers:
 * 🟠 Solo / 🟢 Couple: $9.95 / $29.95 / $49.95 / $99.95 / $149.95 / $199.95
 * 🟡 Full Sextapes: $99.95 / $199.95
 * 🔴 Bundles: $19.95 / $29.95 / $49.95
 * 🟣 Customs: min $49.95
 * 🔵 Teaser: Free / $9.95
 * ⚪️ GFE/Rapport: $9.95 / $19.95 / $29.95
 */
@Service
@Slf4j
public class PricingLadderService {

    public static final double FREE = 0.0;
    public static final double P_9_95 = 9.95;
    public static final double P_19_95 = 19.95;
    public static final double P_29_95 = 29.95;
    public static final double P_49_95 = 49.95;
    public static final double P_99_95 = 99.95;
    public static final double P_149_95 = 149.95;
    public static final double P_199_95 = 199.95;

    private static final List<Double> LADDER_SOLO_COUPLE = List.of(P_9_95, P_29_95, P_49_95, P_99_95, P_149_95, P_199_95);
    private static final List<Double> LADDER_FULL_SEXTAPE = List.of(P_99_95, P_199_95);
    private static final List<Double> LADDER_BUNDLE = List.of(P_19_95, P_29_95, P_49_95);
    private static final List<Double> LADDER_GFE = List.of(P_9_95, P_19_95, P_29_95);
    private static final List<Double> LADDER_TEASER = List.of(FREE, P_9_95);
    private static final List<Double> LADDER_CUSTOM_MIN = List.of(P_49_95, P_99_95, P_149_95, P_199_95);

    private static final List<Double> LADDER = LADDER_SOLO_COUPLE;
    private static final List<String> TIER1_COUNTRY_CODES = List.of("US", "USA", "UK", "AU", "AUSTRALIA", "CA", "CANADA", "DE", "GERMANY");

    @Value("${pricing.active-reply-minutes:10}")
    private int activeReplyMinutes = 10;

    /** Allowed prices for Solo/Couple (default ladder). */
    public List<Double> getLadder() {
        return LADDER;
    }

    /** Allowed pricing tiers for a category (colour code system). */
    public List<Double> getAllowedPricesForCategory(ContentCategory category) {
        if (category == null) return LADDER_SOLO_COUPLE;
        return switch (category) {
            case SOLO, COUPLE -> LADDER_SOLO_COUPLE;
            case FULL_SEXTAPE -> LADDER_FULL_SEXTAPE;
            case BUNDLE -> LADDER_BUNDLE;
            case GFE_RAPPORT -> LADDER_GFE;
            case TEASER -> LADDER_TEASER;
            case CUSTOM -> LADDER_CUSTOM_MIN;
        };
    }

    /** Round to nearest allowed price for the category; cap to category min/max. */
    public double roundToCategoryLadder(double amount, ContentCategory category) {
        if (category == null) return roundToLadder(amount);
        List<Double> allowed = getAllowedPricesForCategory(category);
        if (amount <= 0) {
            return allowed.contains(FREE) ? FREE : allowed.stream().filter(p -> p > 0).findFirst().orElse(allowed.get(0));
        }
        double minPaid = allowed.stream().filter(p -> p > 0).findFirst().orElse(allowed.get(0));
        double maxP = allowed.get(allowed.size() - 1);
        if (amount < minPaid) return minPaid;
        if (amount > maxP) return maxP;
        for (double p : allowed) {
            if (p > 0 && Math.abs(amount - p) < 0.50) return p;
        }
        for (int i = 0; i < allowed.size() - 1; i++) {
            if (amount >= allowed.get(i) && amount < allowed.get(i + 1)) return allowed.get(i);
        }
        return maxP;
    }

    /** Round to nearest .95 on default (Solo/Couple) ladder. */
    public double roundToLadder(double amount) {
        return roundToCategoryLadder(amount, ContentCategory.SOLO);
    }

    /**
     * Whether fan is in a Tier 1 country (US, UK, AU, CA, DE). Uses stored country.
     */
    public boolean isTier1Country(Fan fan) {
        String c = fan.getCountry();
        if (c == null || c.isBlank()) return false;
        String upper = c.trim().toUpperCase();
        for (String code : TIER1_COUNTRY_CODES) {
            if (upper.equals(code) || upper.startsWith(code) || upper.contains(code)) return true;
        }
        return false;
    }

    /**
     * Active = replied within X minutes (e.g. 5 or 10) to at least one of our last 2–3 messages.
     * We store lastQuickReplyAt when that happens; if it's recent we consider them active.
     */
    public boolean isActiveReplier(Fan fan) {
        if (fan.getLastQuickReplyAt() == null) return false;
        long minutesSince = java.time.Duration.between(fan.getLastQuickReplyAt(), java.time.LocalDateTime.now()).toMinutes();
        return minutesSince <= activeReplyMinutes;
    }

    /**
     * Start price based on fan location, engagement pace, and request detail.
     * High-value or active fans can be opened at $49.95+ immediately after a non-explicit wallet test.
     *
     * - First ever: free teaser (handled by caller).
     * - Fan is new, location unknown, low engagement → $9.95 (low barrier entry; get first purchase, upsell from there).
     * - Fan is active, responding fast, engaged → $29.95–$49.95 (momentum is high; price to match energy).
     * - Fan from Tier 1 (US, UK, AU, CA, DE) → $29.95–$49.95 opening; never open at lowest price.
     * - Fan requests specific/niche content → $49.95–$99.95 (specificity = value; detailed request = willingness to pay more).
     * - Fan has bought 2–3 times in one session → $99.95+ (buying momentum at peak; offer full tape or bundle now).
     * - Fan is re-engaging after going cold → $9.95–$19.95 (re-warm at low friction; escalate once active again).
     * - Custom request (named video) → min $49.95 (always above standard; name = personalisation premium).
     * - After free teaser, not passed wallet test: $9.95 default; $29.95–$49.95 if Tier1 or active; $49.95+ if specific request.
     * - Immediately after wallet test: high-value OR active OR Tier1 → $49.95+; specific request + engaged → up to $99.95.
     */
    public double determineStartPrice(Fan fan, boolean hasReceivedFreeTeaser, Double totalSpent) {
        return determineStartPrice(fan, hasReceivedFreeTeaser, totalSpent, false, 0, false);
    }

    public double determineStartPrice(Fan fan, boolean hasReceivedFreeTeaser, Double totalSpent, boolean hasSpecificRequest) {
        return determineStartPrice(fan, hasReceivedFreeTeaser, totalSpent, hasSpecificRequest, 0, false);
    }

    public double determineStartPrice(Fan fan, boolean hasReceivedFreeTeaser, Double totalSpent, boolean hasSpecificRequest, int purchasesInSession) {
        return determineStartPrice(fan, hasReceivedFreeTeaser, totalSpent, hasSpecificRequest, purchasesInSession, false);
    }

    /**
     * @param hasSpecificRequest true when fan asked for something specific/detailed.
     * @param purchasesInSession number of PPV unlocks in the current session (e.g. last 2 hours). 2+ → $99.95+.
     * @param reengagingAfterCold true when fan was inactive for several days and is now messaging again → $9.95–$19.95.
     */
    public double determineStartPrice(Fan fan, boolean hasReceivedFreeTeaser, Double totalSpent, boolean hasSpecificRequest, int purchasesInSession, boolean reengagingAfterCold) {
        boolean passedWalletTest = Boolean.TRUE.equals(fan.getPassedWalletTest());
        boolean tier1 = isTier1Country(fan);
        boolean active = isActiveReplier(fan);
        boolean highValueOrActive = tier1 || active || hasSpecificRequest;

        if (!hasReceivedFreeTeaser) {
            return FREE;
        }

        if (reengagingAfterCold) {
            return P_9_95;
        }
        if (!passedWalletTest) {
            if (highValueOrActive) {
                return (active && tier1) || hasSpecificRequest ? P_49_95 : P_29_95;
            }
            return P_9_95;
        }

        if (purchasesInSession >= 2) {
            return P_99_95;
        }
        if (totalSpent != null && totalSpent >= 100) {
            return active ? P_99_95 : P_49_95;
        }
        if (totalSpent != null && totalSpent >= 50) {
            return highValueOrActive ? P_49_95 : P_29_95;
        }
        if (hasSpecificRequest && (active || tier1)) {
            return P_99_95;
        }
        if (highValueOrActive) {
            return P_49_95;
        }
        return P_9_95;
    }

    /** Price tier index 0–6 for backward compat (0=free, 1=9.95, 2=29.95, 3=49.95, 4=99.95, 5=149.95, 6=199.95). */
    public int priceToTierIndex(double price) {
        if (price <= 0) return 0;
        if (price <= 10) return 1;
        if (price <= 35) return 2;
        if (price <= 75) return 3;
        if (price <= 125) return 4;
        if (price <= 175) return 5;
        return 6;
    }
}
