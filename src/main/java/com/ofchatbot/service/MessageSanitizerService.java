package com.ofchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sanitizes message text before sending to OnlyFans API.
 * OnlyFans rejects messages containing "restricted words" (400 Bad Request).
 * This service replaces or removes known trigger terms to avoid send failures.
 */
@Service
@Slf4j
public class MessageSanitizerService {

    /** Case-insensitive patterns and replacement text. Order matters for overlapping terms. */
    private static final Map<Pattern, String> RESTRICTED_REPLACEMENTS = new LinkedHashMap<>();

    static {
        // Terms that triggered OnlyFans "Input contains restricted words" (from API error textHighLight)
        add("enema", "that");
        add("\\bdirty\\b", "naughty");
        // Common platform-filter triggers — add more as you discover them from 400 responses
        add("\\bscat\\b", "that");
        add("\\brape\\b", "that");
        add("\\bpedo\\b", "that");
        add("\\bunderage\\b", "that");
        add("\\bminor\\b", "that");
        add("\\bchild\\b", "that");
        add("\\bincest\\b", "that");
        add("\\bbeastiality\\b", "that");
        add("\\bnon-?consent\\b", "that");
        add("\\bnonconsensual\\b", "that");
    }

    private static void add(String regex, String replacement) {
        RESTRICTED_REPLACEMENTS.put(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement);
    }

    @Value("${onlyfans.message.sanitize.enabled:true}")
    private boolean sanitizeEnabled;

    /**
     * Sanitizes text so it is less likely to trigger OnlyFans "Input contains restricted words".
     * Returns the original string if sanitization is disabled.
     */
    public String sanitizeForOnlyFans(String messageText) {
        if (messageText == null || messageText.isEmpty()) {
            return messageText;
        }
        if (!sanitizeEnabled) {
            return messageText;
        }
        String out = messageText;
        for (Map.Entry<Pattern, String> e : RESTRICTED_REPLACEMENTS.entrySet()) {
            String before = out;
            out = e.getKey().matcher(out).replaceAll(e.getValue());
            if (!out.equals(before)) {
                log.debug("Sanitized OnlyFans message: replaced trigger for pattern {}", e.getKey().pattern());
            }
        }
        return out;
    }
}
