package com.ofchatbot.service;

import com.ofchatbot.dto.GoHighLevelConversationResponse;
import com.ofchatbot.dto.GoHighLevelMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoHighLevelService {
    
    private final RestTemplate restTemplate;
    
    @Value("${gohighlevel.api.token}")
    private String apiToken;
    
    @Value("${gohighlevel.api.base-url}")
    private String baseUrl;
    
    public GoHighLevelConversationResponse searchConversations(String contactId, String locationId) {
        String url = baseUrl + "/conversations/search?contactId=" + contactId;
        
        HttpHeaders headers = createHeaders();
        Map<String, String> body = new HashMap<>();
        body.put("locationId", locationId);
        body.put("contactId", contactId);
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<GoHighLevelConversationResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, request, GoHighLevelConversationResponse.class
            );
            log.info("Successfully searched conversations for contact: {}", contactId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to search conversations for contact: {}", contactId, e);
            throw new RuntimeException("Failed to search conversations", e);
        }
    }
    
    public GoHighLevelMessageResponse getConversationMessages(String conversationId, String contactId, String locationId) {
        String url = baseUrl + "/conversations/" + conversationId + "/messages?contactId=" + contactId;
        
        HttpHeaders headers = createHeaders();
        Map<String, String> body = new HashMap<>();
        body.put("locationId", locationId);
        body.put("contactId", contactId);
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<GoHighLevelMessageResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, GoHighLevelMessageResponse.class
            );
            log.info("Successfully retrieved messages for conversation: {}", conversationId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get messages for conversation: {}", conversationId, e);
            throw new RuntimeException("Failed to get conversation messages", e);
        }
    }
    
    public void sendMessage(String conversationId, String contactId, String locationId, String message, String platform) {
        String url = baseUrl + "/conversations/messages?contactId=" + contactId;
        
        HttpHeaders headers = createHeaders();
        Map<String, String> body = new HashMap<>();
        body.put("locationId", locationId);
        body.put("contactId", contactId);
        body.put("conversationId", conversationId);
        body.put("message", message);
        body.put("type", platform.toUpperCase());
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Successfully sent message to conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to send message to conversation: {}", conversationId, e);
            throw new RuntimeException("Failed to send message", e);
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set("Version", "2021-04-15");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
