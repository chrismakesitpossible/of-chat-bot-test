package com.ofchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MessageBatchingService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    private final Map<String, BatchState> activeBatches = new ConcurrentHashMap<>();

    public void handleIncomingMessage(String fanId, Runnable processMessageTask) {
        BatchState batch = activeBatches.computeIfAbsent(fanId, k -> new BatchState());
        
        synchronized (batch) {
            if (batch.scheduledTask != null) {
                batch.scheduledTask.cancel(false);
                log.info("Cancelled previous batch task for fan: {}", fanId);
            }
            
            batch.messageCount++;
            batch.lastMessageTime = LocalDateTime.now();
            
            batch.scheduledTask = scheduler.schedule(() -> {
                synchronized (batch) {
                    try {
                        log.info("Processing batched messages for fan: {} (count: {})", fanId, batch.messageCount);
                        processMessageTask.run();
                    } catch (Exception e) {
                        log.error("CRITICAL: Message processing failed for fan {} — fan got silence!", fanId, e);
                    } finally {
                        activeBatches.remove(fanId);
                    }
                }
            }, 7, TimeUnit.SECONDS);
            
            log.info("Batching message for fan: {} (count: {}, will process in 7s)", fanId, batch.messageCount);
        }
    }

    public boolean isInBatch(String fanId) {
        return activeBatches.containsKey(fanId);
    }

    private static class BatchState {
        int messageCount = 0;
        LocalDateTime lastMessageTime;
        ScheduledFuture<?> scheduledTask;
    }
}
