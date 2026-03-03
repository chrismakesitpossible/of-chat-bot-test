package com.ofchatbot.service;

import com.ofchatbot.entity.ContentCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Maps OnlyFans vault folder name (e.g. "SHOWER SCRIPT", "HOUSE CLEAN SCRIPT") to content category.
 * Folder names come from the OF vault list "name" field (updates.txt folder names).
 */
@Component
@Slf4j
public class ContentCategoryResolver {

    /** Known Solo script folder names from updates.txt (S01–S30 style). */
    private static final Set<String> SOLO_FOLDER_NAMES = Set.of(
        "SHOWER SCRIPT", "HOUSE CLEAN SCRIPT", "COOKING SCRIPT", "NIGHT OUT", "BEFORE GYM", "AFTER GYM",
        "FAN BEFORE WORK TEASE", "FAN AFTER WORK TEASE", "DOMINANT SCRIPT", "SUBMISSIVE SCRIPT",
        "SMALL COCK HUMILIATION", "JOI — JERK OFF INSTRUCTION", "BEFORE PARTY", "AFTER PARTY",
        "PYJAMA / SLEEPWEAR", "FORMAL / OFFICE LOOK", "SCHOOL GIRL", "KITCHEN SCENE", "FEET CONTENT",
        "HEADPHONES / MUSIC GIRL", "BLACKBODY / LEATHER", "BLACK LINGERIE", "CASUAL HOME — SHORTS",
        "CASUAL HOME — OILY", "DRESS AND TIGHTS", "DOMINA", "WHITE SHIRT SCENE", "SKIRT AND SHIRT",
        "BATHROOM TWERKING", "OFFICE CHAIR SOLO"
    );

    /** Couple script folder name prefixes/patterns from updates.txt (C01–C16). */
    private static final Set<String> COUPLE_FOLDER_NAMES = Set.of(
        "BEDROOM ROMANCE", "HOTEL ROOM", "DOMINANT / ROUGH", "SUBMISSIVE / UNWILLING", "QUICKIE / SPONTANEOUS",
        "MORNING SEX", "COUCH / LIVING ROOM", "SHOWER / BATHROOM", "HOTEL COUCH", "CAR SEX",
        "COSPLAY / ROLEPLAY", "VACATION TAPE", "LINGERIE FEATURE", "CUSTOM COUPLE", "MASSAGE HAPPY END",
        "FULL SEXTAPE (UNCUT)"
    );

    /**
     * Resolve content category from vault list folder name (OnlyFans list "name").
     * Fallback: SOLO for unknown names so we always have a category.
     */
    public ContentCategory fromFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            log.debug("Empty folder name, defaulting to SOLO");
            return ContentCategory.SOLO;
        }
        String normalized = folderName.trim().toUpperCase();

        if (normalized.contains("FULL") && (normalized.contains("SEXTAPE") || normalized.contains("TAPE"))) {
            return ContentCategory.FULL_SEXTAPE;
        }
        if (normalized.contains("BUNDLE") || normalized.contains("SHORT TRIM") || normalized.contains("LONGER TRIM") || normalized.contains("THEME BUNDLE")) {
            return ContentCategory.BUNDLE;
        }
        if (normalized.contains("GFE") || normalized.contains("RAPPORT")) {
            return ContentCategory.GFE_RAPPORT;
        }
        if (normalized.contains("CUSTOM") && normalized.contains("COUPLE")) {
            return ContentCategory.CUSTOM;
        }
        if (normalized.contains("TEASER")) {
            return ContentCategory.TEASER;
        }

        for (String couple : COUPLE_FOLDER_NAMES) {
            if (matchesFolderPattern(normalized, couple)) {
                return ContentCategory.COUPLE;
            }
        }
        for (String solo : SOLO_FOLDER_NAMES) {
            if (matchesFolderPattern(normalized, solo)) {
                return ContentCategory.SOLO;
            }
        }
        if (normalized.contains("SCRIPT") || normalized.contains("COUPLE") || normalized.startsWith("C0")) {
            return normalized.contains("COUPLE") || normalized.startsWith("C0") ? ContentCategory.COUPLE : ContentCategory.SOLO;
        }

        log.debug("Unknown folder name '{}', defaulting to SOLO", folderName);
        return ContentCategory.SOLO;
    }

    public String toDbValue(ContentCategory category) {
        return category == null ? null : category.name();
    }

    /**
     * Lenient pattern match between a vault folder name and a known script title.
     * - Case-insensitive
     * - Ignores underscores, slashes, and extra words/prefixes (e.g. "SH_S01_SHOWER_SCRIPT")
     * - Requires that all meaningful words from the pattern appear somewhere in the folder name.
     */
    private boolean matchesFolderPattern(String normalizedFolderName, String pattern) {
        if (normalizedFolderName == null || pattern == null) return false;

        // Fast path: direct substring match on the raw normalized string.
        if (normalizedFolderName.contains(pattern)) {
            return true;
        }

        // Tokenize both strings on non-alphanumeric separators (spaces, underscores, slashes, etc.).
        String cleanedName = normalizedFolderName.replaceAll("[^A-Z0-9]+", " ").trim();
        String cleanedPattern = pattern.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim();

        if (cleanedName.isEmpty() || cleanedPattern.isEmpty()) {
            return false;
        }

        java.util.Set<String> nameTokens = new java.util.HashSet<>(java.util.Arrays.asList(cleanedName.split("\\s+")));
        String[] patternTokens = cleanedPattern.split("\\s+");

        // We consider it a match only if *all* pattern tokens appear in the folder name tokens,
        // but the folder name can have extra tokens/prefixes/suffixes.
        for (String token : patternTokens) {
            if (!nameTokens.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
