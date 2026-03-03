package com.ofchatbot.service;

import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeakInterestDetectionService {

    private static final int PEAK_INTENSITY_THRESHOLD = 8;
    private static final int RISING_INTENSITY_THRESHOLD = 6;
    private static final int MIN_MESSAGES_FOR_PEAK = 5;

    public boolean isPeakInterestMoment(ConversationState state, List<Message> recentMessages) {
        if (state.getIntensityLevel() == null) {
            return false;
        }

        int currentIntensity = state.getIntensityLevel();

        if (currentIntensity >= PEAK_INTENSITY_THRESHOLD) {
            log.info("Peak interest detected: intensity at maximum level {}", currentIntensity);
            return true;
        }

        if (currentIntensity >= RISING_INTENSITY_THRESHOLD && isIntensityRising(state, recentMessages)) {
            log.info("Peak interest detected: intensity rising rapidly to {}", currentIntensity);
            return true;
        }

        if (isHighEngagementPhase(state) && hasRapidResponsePattern(recentMessages)) {
            log.info("Peak interest detected: high engagement phase with rapid responses");
            return true;
        }

        if (hasExcitementIndicators(recentMessages) && currentIntensity >= RISING_INTENSITY_THRESHOLD) {
            log.info("Peak interest detected: excitement indicators with intensity {}", currentIntensity);
            return true;
        }

        return false;
    }

    private boolean isIntensityRising(ConversationState state, List<Message> recentMessages) {
        if (recentMessages.size() < MIN_MESSAGES_FOR_PEAK) {
            return false;
        }

        int currentIntensity = state.getIntensityLevel();

        long recentBotMessages = recentMessages.stream()
            .filter(m -> "bot".equals(m.getRole()))
            .filter(m -> ChronoUnit.MINUTES.between(m.getTimestamp(), LocalDateTime.now()) < 10)
            .count();

        if (recentBotMessages >= 3 && currentIntensity >= RISING_INTENSITY_THRESHOLD) {
            return true;
        }

        return false;
    }

    private boolean isHighEngagementPhase(ConversationState state) {
        if (state.getCurrentPhase() == null) {
            return false;
        }

        String phase = state.getCurrentPhase();
        return phase.equals("SUGGESTIVE") || phase.equals("INTIMATE");
    }

    private boolean hasRapidResponsePattern(List<Message> recentMessages) {
        if (recentMessages.size() < 4) {
            return false;
        }

        List<Message> userMessages = recentMessages.stream()
            .filter(m -> "user".equals(m.getRole()))
            .filter(m -> ChronoUnit.MINUTES.between(m.getTimestamp(), LocalDateTime.now()) < 5)
            .toList();

        if (userMessages.size() >= 3) {
            log.debug("Rapid response pattern detected: {} user messages in 5 minutes", userMessages.size());
            return true;
        }

        return false;
    }

    private boolean hasExcitementIndicators(List<Message> recentMessages) {
        List<Message> recentUserMessages = recentMessages.stream()
            .filter(m -> "user".equals(m.getRole()))
            .filter(m -> ChronoUnit.MINUTES.between(m.getTimestamp(), LocalDateTime.now()) < 5)
            .toList();

        for (Message message : recentUserMessages) {
            String text = message.getText().toLowerCase();

            long exclamationCount = text.chars().filter(ch -> ch == '!').count();
            if (exclamationCount >= 2) {
                log.debug("Excitement indicator: multiple exclamation marks");
                return true;
            }

            if (text.contains("omg") || text.contains("wow") || text.contains("damn") ||
                text.contains("fuck") || text.contains("yes!") || text.contains("hell yes")) {
                log.debug("Excitement indicator: excitement keywords");
                return true;
            }

            if (text.length() < 50 && text.chars().filter(ch -> ch == '😍' || ch == '🔥' ||
                ch == '💦' || ch == '😈' || ch == '🥵').count() >= 2) {
                log.debug("Excitement indicator: multiple excitement emojis");
                return true;
            }
        }

        return false;
    }

    public String getPeakInterestReason(ConversationState state, List<Message> recentMessages) {
        int currentIntensity = state.getIntensityLevel() != null ? state.getIntensityLevel() : 0;

        if (currentIntensity >= PEAK_INTENSITY_THRESHOLD) {
            return "Maximum intensity level";
        }

        if (isIntensityRising(state, recentMessages)) {
            return "Rapidly rising intensity";
        }

        if (isHighEngagementPhase(state) && hasRapidResponsePattern(recentMessages)) {
            return "High engagement with rapid responses";
        }

        if (hasExcitementIndicators(recentMessages)) {
            return "Strong excitement indicators";
        }

        return "Peak interest detected";
    }
}
