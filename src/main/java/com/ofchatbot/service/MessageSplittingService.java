package com.ofchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageSplittingService {

    /** Blank line: one or more newlines, optionally with spaces/tabs in between. Never send one message with this in the middle. */
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    public List<String> splitIntoNaturalMessages(String fullResponse) {
        List<String> messages = new ArrayList<>();

        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            return messages;
        }

        String cleaned = fullResponse.trim();
        String normalized = cleaned.replace("\r\n", "\n").replace("\r", "\n");

        // First: if there's any blank line (empty row), split there so each part is its own message. Never one bubble with a gap.
        if (BLANK_LINE.matcher(normalized).find()) {
            String[] parts = BLANK_LINE.split(normalized);
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    messages.add(trimmed);
                }
            }
            if (!messages.isEmpty()) {
                log.info("Split response into {} messages (blank-line split)", messages.size());
                return messages;
            }
        }

        // Single newline (no blank row): also split so "line1\nline2" becomes two messages
        if (normalized.contains("\n")) {
            String[] blocks = normalized.split("\\n+");
            for (String block : blocks) {
                String trimmedBlock = block.trim();
                if (!trimmedBlock.isEmpty()) {
                    messages.add(trimmedBlock);
                }
            }
            if (!messages.isEmpty()) {
                log.info("Split response into {} messages (newline split)", messages.size());
                return messages;
            }
        }

        // No newlines: light sentence-based splitting to avoid one huge wall of text
        Pattern sentencePattern = Pattern.compile("([^.!?]+[.!?]+|[^.!?]+$)");
        Matcher matcher = sentencePattern.matcher(cleaned);

        StringBuilder currentMessage = new StringBuilder();
        final int MAX_LENGTH = 180;

        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.isEmpty()) {
                continue;
            }

            if (currentMessage.length() == 0) {
                currentMessage.append(sentence);
            } else if (currentMessage.length() + sentence.length() + 1 <= MAX_LENGTH) {
                currentMessage.append(" ").append(sentence);
            } else {
                messages.add(currentMessage.toString().trim());
                currentMessage = new StringBuilder(sentence);
            }
        }

        if (currentMessage.length() > 0) {
            messages.add(currentMessage.toString().trim());
        }

        if (messages.isEmpty()) {
            messages.add(cleaned);
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
