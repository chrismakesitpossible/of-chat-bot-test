package com.ofchatbot.service;

import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResponseTimingService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Random random = new Random();
    private final OnlyFansApiService onlyFansApiService;
    private final MessageSplittingService messageSplittingService;
    private final TransactionTemplate transactionTemplate;

    /** Pending natural-response tasks per chat. When we schedule a delayed PPV (purchase intent), we cancel these so we don't also send "i know babe" etc. */
    private final ConcurrentHashMap<String, List<ScheduledFuture<?>>> pendingNaturalByChat = new ConcurrentHashMap<>();

    @Value("${purchase-intent.ppv.delay-seconds:90}")
    private int purchaseIntentPpvDelaySeconds = 90;
    @Value("${purchase-intent.typing-indicator-count:3}")
    private int purchaseIntentTypingCount = 3;

    public void scheduleNaturalResponse(
            String chatId,
            String responseMessage,
            ConversationState state,
            Fan fan,
            String fanMessage,
            String replyToMessageId,
            String creatorId
    ) {
        List<String> messages = messageSplittingService.splitIntoNaturalMessages(responseMessage);
        
        if (messages.isEmpty()) {
            log.warn("No messages to send after splitting");
            return;
        }
        
        long initialDelaySeconds = calculateInitialDelay(state, fan);
        
        log.info("Scheduling {} messages for chat {}: initial delay {}s",
                messages.size(), chatId, initialDelaySeconds);
        
        List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();
        long currentDelay = initialDelaySeconds;
        
        for (int i = 0; i < messages.size(); i++) {
            String message = messages.get(i);
            long typingDuration = calculateTypingDuration(message);
            boolean isFirstMessage = (i == 0);
            boolean isLastMessage = (i == messages.size() - 1);
            String messageReplyTo = isFirstMessage ? replyToMessageId : null;
            
            final long typingDelay = currentDelay;
            
            ScheduledFuture<?> typingFuture = scheduler.schedule(() -> {
                try {
                    onlyFansApiService.sendTypingIndicator(chatId, creatorId);
                    log.info("Sent typing indicator to chat: {}", chatId);
                } catch (Exception e) {
                    log.error("Failed to send typing indicator", e);
                } finally {
                    removeAndCleanup(chatId, futures, null);
                }
            }, typingDelay, TimeUnit.SECONDS);
            futures.add(typingFuture);
            
            long messageSendDelay = currentDelay + typingDuration;
            final int msgIndex = i + 1;
            final int totalMessages = messages.size();
            ScheduledFuture<?> messageFuture = scheduler.schedule(() -> {
                try {
                    onlyFansApiService.sendMessage(chatId, message, messageReplyTo, creatorId);
                    log.info("Sent message {} of {} to chat: {}", msgIndex, totalMessages, chatId);
                } catch (Exception e) {
                    log.error("Failed to send message", e);
                } finally {
                    removeAndCleanup(chatId, futures, null);
                }
            }, messageSendDelay, TimeUnit.SECONDS);
            futures.add(messageFuture);
            
            currentDelay = messageSendDelay + (isLastMessage ? 0 : (3 + random.nextInt(5)));
        }
        pendingNaturalByChat.put(chatId, futures);
    }

    private void removeAndCleanup(String chatId, List<ScheduledFuture<?>> list, ScheduledFuture<?> one) {
        if (one != null) list.remove(one);
        list.removeIf(f -> f.isDone() || f.isCancelled());
        if (list.isEmpty()) pendingNaturalByChat.remove(chatId);
    }

    private long calculateInitialDelay(ConversationState state, Fan fan) {
        int baseDelaySeconds;

        long minutesSinceLastMessage = ChronoUnit.MINUTES.between(
                fan.getLastUpdated() != null ? fan.getLastUpdated() : LocalDateTime.now().minusHours(1),
                LocalDateTime.now()
        );

        String scriptCategory = state.getLastScriptCategory();
        boolean isHotConversation = scriptCategory != null && (
                scriptCategory.equals("SEXTING_SESSION") ||
                scriptCategory.equals("PPV_OFFER") ||
                scriptCategory.equals("LOCK_SALE")
        );

        // Active back-and-forth: fan replied within 5 min → fast response (Issue #7)
        boolean isActiveBackAndForth = minutesSinceLastMessage < 5 || isHotConversation;

        if (isActiveBackAndForth) {
            // 30-90 second delay for active conversations — no time-of-day multiplier
            baseDelaySeconds = 30 + random.nextInt(60);
        } else if (minutesSinceLastMessage < 15) {
            baseDelaySeconds = 120 + random.nextInt(240);
        } else if (minutesSinceLastMessage < 60) {
            baseDelaySeconds = 300 + random.nextInt(600);
        } else {
            baseDelaySeconds = 600 + random.nextInt(900);
        }

        // Only apply time-of-day multiplier to non-active conversations (Issue #7)
        if (!isActiveBackAndForth) {
            int hourOfDay = LocalDateTime.now().getHour();
            double timeMultiplier = getTimeOfDayMultiplier(hourOfDay);
            baseDelaySeconds = (int) (baseDelaySeconds * timeMultiplier);
        }

        int variance = (int) (baseDelaySeconds * 0.3);
        int finalDelay = variance > 0 ? baseDelaySeconds + (random.nextInt(variance * 2) - variance) : baseDelaySeconds;

        int maxDelaySeconds = 30 * 60;
        return Math.min(Math.max(finalDelay, 30), maxDelaySeconds);
    }

    private double getTimeOfDayMultiplier(int hour) {
        if (hour >= 3 && hour < 6) {
            return 2.0; // was 5.0 — reduced (Issue #7)
        } else if (hour >= 6 && hour < 9) {
            return 1.5;
        } else if (hour >= 9 && hour < 12) {
            return 1.2;
        } else if (hour >= 12 && hour < 17) {
            return 1.0;
        } else if (hour >= 17 && hour < 22) {
            return 0.8;
        } else if (hour >= 22 || hour < 3) {
            return 0.7;
        }
        return 1.0;
    }

    private long calculateTypingDuration(String message) {
        int messageLength = message.length();
        int baseTypingSeconds;

        if (messageLength < 50) {
            baseTypingSeconds = 5 + random.nextInt(8);
        } else if (messageLength < 150) {
            baseTypingSeconds = 8 + random.nextInt(11);
        } else {
            baseTypingSeconds = 12 + random.nextInt(14);
        }

        int variance = (int) (baseTypingSeconds * 0.4);
        int finalTyping = baseTypingSeconds + (random.nextInt(variance * 2) - variance);

        return Math.max(finalTyping, 3);
    }

    /**
     * When explicit purchase intent is detected: wait at least 1m30s, show typing a few times, then send the PPV so it doesn't look like an instant bot.
     * Cancels any pending natural-response messages for this chat so we don't also send "i know babe" etc. (e.g. when the same thought came in two webhooks).
     */
    public void scheduleDelayedPPVWithTyping(String chatId, String creatorId, Runnable sendPpvTask) {
        cancelPendingNaturalResponsesForChat(chatId);
        int delaySeconds = Math.max(purchaseIntentPpvDelaySeconds, 90);
        int typingCount = Math.min(Math.max(purchaseIntentTypingCount, 2), 5);
        log.info("Scheduling PPV for chat {} in {}s with {} typing indicators (purchase intent)", chatId, delaySeconds, typingCount);

        for (int i = 0; i < typingCount; i++) {
            long typingAt = (long) (delaySeconds * (i + 1) / (typingCount + 1));
            final long at = typingAt;
            scheduler.schedule(() -> {
                try {
                    onlyFansApiService.sendTypingIndicator(chatId, creatorId);
                    log.debug("Purchase-intent delay: sent typing indicator to chat {} at {}s", chatId, at);
                } catch (Exception e) {
                    log.warn("Failed to send typing indicator during purchase-intent delay", e);
                }
            }, at, TimeUnit.SECONDS);
        }

        scheduler.schedule(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> sendPpvTask.run());
            } catch (Exception e) {
                log.error("Failed to send PPV after purchase-intent delay", e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /** Cancel any pending natural-response messages for this chat so purchase-intent PPV doesn't double up with "i know babe" etc. */
    public void cancelPendingNaturalResponsesForChat(String chatId) {
        List<ScheduledFuture<?>> list = pendingNaturalByChat.remove(chatId);
        if (list != null && !list.isEmpty()) {
            int cancelled = 0;
            for (ScheduledFuture<?> f : list) {
                if (!f.isDone() && f.cancel(false)) cancelled++;
            }
            if (cancelled > 0) {
                log.info("Cancelled {} pending natural-response task(s) for chat {} (purchase intent PPV will send instead)", cancelled, chatId);
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
