package com.ofchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageSplittingService {

    /** Blank line: one or more newlines, optionally with spaces/tabs in between. Never send one message with this in the middle. */
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    /** Hard cap: never send more than 2 messages from one response (Issue #17). */
    private static final int MAX_MESSAGES = 2;

    public List<String> splitIntoNaturalMessages(String fullResponse) {
        List<String> messages = new ArrayList<>();

        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            return messages;
        }

        String cleaned = fullResponse.trim();
        String normalized = cleaned.replace("\r\n", "\n").replace("\r", "\n");

        // Split on blank lines first, then single newlines
        if (BLANK_LINE.matcher(normalized).find()) {
            String[] parts = BLANK_LINE.split(normalized);
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    messages.add(trimmed);
                }
            }
        } else if (normalized.contains("\n")) {
            String[] blocks = normalized.split("\\n+");
            for (String block : blocks) {
                String trimmedBlock = block.trim();
                if (!trimmedBlock.isEmpty()) {
                    messages.add(trimmedBlock);
                }
            }
        }

        if (messages.isEmpty()) {
            messages.add(cleaned);
        }

        // Hard cap: combine everything beyond MAX_MESSAGES into the last allowed message
        if (messages.size() > MAX_MESSAGES) {
            List<String> capped = new ArrayList<>();
            capped.add(messages.get(0));
            StringBuilder rest = new StringBuilder();
            for (int i = 1; i < messages.size(); i++) {
                if (rest.length() > 0) rest.append(" ");
                rest.append(messages.get(i));
            }
            capped.add(rest.toString());
            messages = capped;
            log.info("Capped response to {} messages (was {})", MAX_MESSAGES, messages.size());
        }

        log.info("Split response into {} messages", messages.size());
        return messages;
    }

    /**
     * Split text on blank lines only. Use when you need to ensure "TEXT1\n\nTEXT2" becomes two separate sends.
     * Returns a list of non-empty trimmed parts.
     */
    public static List<String> splitOnBlankLine(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (!BLANK_LINE.matcher(normalized).find()) {
            return List.of(normalized);
        }
        return Arrays.stream(BLANK_LINE.split(normalized))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
