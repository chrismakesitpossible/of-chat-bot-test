package com.ofchatbot.service;

import com.ofchatbot.entity.Conversation;
import com.ofchatbot.entity.ConversationState;
import com.ofchatbot.entity.Fan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReEngagementService {
    
    private final FanService fanService;
    private final ConversationService conversationService;
    private final ScriptEngineService scriptEngineService;
    private final AnthropicService anthropicService;
    private final OnlyFansApiService onlyFansApiService;
    private final MessageService messageService;
    private final ErrorLogService errorLogService;
    
    @Scheduled(cron = "0 0 10 * * *")
    public void processInactiveFans() {
        log.info("Starting re-engagement check for inactive fans");
        
        try {
            LocalDateTime twoWeeksAgo = LocalDateTime.now().minus(14, ChronoUnit.DAYS);
            List<Fan> inactiveFans = fanService.findInactiveFans(twoWeeksAgo);
            
            log.info("Found {} inactive fans to re-engage", inactiveFans.size());
            
            for (Fan fan : inactiveFans) {
                try {
                    sendReEngagementMessage(fan);
                } catch (Exception e) {
                    log.error("Error sending re-engagement message to fan: {}", fan.getOnlyfansUsername(), e);
                    errorLogService.logError(
                        "RE_ENGAGEMENT_FAILED",
                        "Failed to send re-engagement message",
                        e,
                        "Fan: " + fan.getOnlyfansUsername()
                    );
                }
            }
            
            log.info("Completed re-engagement processing");
            
        } catch (Exception e) {
            log.error("Error in re-engagement job", e);
            errorLogService.logError(
                "RE_ENGAGEMENT_JOB_FAILED",
                "Re-engagement scheduled job failed",
                e,
                null
            );
        }
    }
    
    private void sendReEngagementMessage(Fan fan) {
        Conversation conversation = conversationService.getOrCreateConversation(fan);
        ConversationState state = scriptEngineService.getOrCreateConversationState(conversation, fan);
        
        String scriptTemplate = scriptEngineService.getScriptTemplate("RE_ENGAGEMENT", state);
        Map<String, String> frameworkGuidance = scriptEngineService.getFrameworkGuidance(state);
        
        String conversationHistory = messageService.getConversationHistory(fan.getOnlyfansUserId(), 5);
        
        // Build real context instead of passing literal "Re-engaging inactive fan" (Issue #8)
        long daysInactive = ChronoUnit.DAYS.between(
            fan.getLastUpdated() != null ? fan.getLastUpdated() : LocalDateTime.now().minusDays(14),
            LocalDateTime.now()
        );
        String contextMessage = String.format(
            "[SYSTEM: This fan has been inactive for %d days. Send a warm, natural re-engagement message. " +
            "Reference past conversation if history is available. Do NOT mention how long they've been gone explicitly.]",
            daysInactive
        );

        String reEngagementMessage = anthropicService.generateScriptBasedResponse(
            contextMessage,
            conversationHistory,
            fan,
            state,
            scriptTemplate,
            frameworkGuidance
        );
        
        onlyFansApiService.sendMessage(fan.getOnlyfansChatId(), reEngagementMessage);
        
        messageService.saveMessage(
            fan.getCreatorId(),
            fan.getOnlyfansUserId(),
            "bot",
            reEngagementMessage,
            LocalDateTime.now().toString(),
            "onlyfans"
        );
        
        fan.setLastUpdated(LocalDateTime.now());
        fanService.saveFan(fan);
        
        log.info("Sent re-engagement message to fan: {}", fan.getOnlyfansUsername());
    }
}
