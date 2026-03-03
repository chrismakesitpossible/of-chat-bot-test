package com.ofchatbot.entity;

/**
 * Colour code system from OF Vault Structure v2.0.
 * Each category has allowed pricing tiers (all prices end in .95).
 */
public enum ContentCategory {
    /** Solo scripts — S01–S30 style folders */
    SOLO,
    /** Couple scripts — C01–C16 */
    COUPLE,
    /** Full uncut sextapes 15 min+, premium only */
    FULL_SEXTAPE,
    /** Short/longer trims, no peak; good for mass/silent fans */
    BUNDLE,
    /** Custom (fan name in video); min $49.95 */
    CUSTOM,
    /** Teaser tag inside Solo/Couple; free or $9.95 */
    TEASER,
    /** GFE/Rapport non-explicit; $9.95 / $19.95 / $29.95 */
    GFE_RAPPORT
}
