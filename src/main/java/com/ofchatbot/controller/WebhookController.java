package com.ofchatbot.controller;

import com.ofchatbot.dto.WebhookPayload;
import com.ofchatbot.service.InstagramChatbotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    
    private final InstagramChatbotService instagramChatbotService;
    
    @PostMapping("/instagram")
    public ResponseEntity<Map<String, String>> handleInstagramWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received Instagram webhook");
        
        try {
            Map<String, Object> body = (Map<String, Object>) payload.get("body");
            
            WebhookPayload webhookPayload = new WebhookPayload();
            webhookPayload.setBody((String) body.get("body"));
            webhookPayload.setTimestamp((String) body.get("timestamp"));
            webhookPayload.setCreatorId((String) body.get("creatorId"));
            webhookPayload.setContactId((String) body.get("contactId"));
            webhookPayload.setIgUsername((String) body.get("igUsername"));
            webhookPayload.setMessageText((String) body.get("messageText"));
            webhookPayload.setPlatform((String) body.get("platform"));
            webhookPayload.setDirection((String) body.get("direction"));
            webhookPayload.setConversationId((String) body.get("conversationId"));
            webhookPayload.setLocationId((String) body.get("locationId"));
            
            new Thread(() -> instagramChatbotService.processIncomingMessage(webhookPayload)).start();
            
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process webhook"));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}
