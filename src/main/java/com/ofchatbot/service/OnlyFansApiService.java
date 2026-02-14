package com.ofchatbot.service;

import com.ofchatbot.dto.OnlyFansSendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyFansApiService {
    
    private final RestTemplate restTemplate;
    private final ErrorLogService errorLogService;
    
    @Value("${onlyfans.api.key}")
    private String apiKey;
    
    @Value("${onlyfans.api.base-url}")
    private String baseUrl;
    
    @Value("${onlyfans.account.id}")
    private String accountId;
    
    public void sendMessage(String chatId, String messageText) {
        String url = String.format("%s/%s/chats/%s/messages", baseUrl, accountId, chatId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        OnlyFansSendMessageRequest request = new OnlyFansSendMessageRequest(messageText);
        HttpEntity<OnlyFansSendMessageRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, httpRequest, String.class
            );
            log.info("Successfully sent message to OnlyFans chat: {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send message to OnlyFans chat: {}", chatId, e);
            errorLogService.logError(
                "ONLYFANS_SEND_MESSAGE_FAILED",
                "Failed to send message to chat: " + chatId,
                e,
                "Message: " + messageText
            );
            throw new RuntimeException("Failed to send OnlyFans message", e);
        }
    }
}
