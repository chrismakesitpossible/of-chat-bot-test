package com.ofchatbot.service;

import com.ofchatbot.dto.OnlyFansSendMessageRequest;
import com.ofchatbot.entity.Creator;
import com.ofchatbot.exception.CannotMessageUserException;
import com.ofchatbot.exception.SendToSelfException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyFansApiService {
    
    private final RestTemplate restTemplate;
    private final ErrorLogService errorLogService;
    private final MessageSanitizerService messageSanitizerService;
    private final CreatorService creatorService;
    
    @Value("${onlyfans.api.base-url}")
    private String baseUrl;

    @Value("${onlyfans.account.id:}")
    private String defaultAccountId;

    @Value("${onlyfans.api.key:}")
    private String defaultApiKey;
    
    public void sendMessage(String chatId, String messageText) {
        sendMessage(chatId, messageText, null, null);
    }
    
    public void sendMessage(String chatId, String messageText, String replyToMessageId) {
        sendMessage(chatId, messageText, replyToMessageId, null);
    }
    
    public void sendMessage(String chatId, String messageText, String replyToMessageId, String creatorId) {
        List<String> parts = splitOnBlankLine(messageText);
        for (int i = 0; i < parts.size(); i++) {
            sendOneMessage(chatId, parts.get(i), i == 0 ? replyToMessageId : null, creatorId);
        }
    }

    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    private static List<String> splitOnBlankLine(String text) {
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

    private void sendOneMessage(String chatId, String messageText, String replyToMessageId, String creatorId) {
        String sanitized = messageSanitizerService.sanitizeForOnlyFans(messageText);
        if (!sanitized.equals(messageText)) {
            log.info("Message sanitized for OnlyFans restricted-words filter before send to chat: {}", chatId);
        }

        String apiKey;
        String accountId;
        
        if (creatorId != null && !creatorId.isBlank()) {
            Creator creator = creatorService.findByCreatorId(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found: " + creatorId));
            apiKey = creator.getOnlyfansApiKey();
            accountId = creator.getOnlyfansAccountId();
            if (apiKey == null || apiKey.isBlank()) apiKey = defaultApiKey;
            if (accountId == null || accountId.isBlank()) {
                accountId = defaultAccountId != null ? defaultAccountId.trim() : "";
                if (!accountId.isBlank())
                    log.warn("Creator {} has no onlyfans_account_id; using default onlyfans.account.id for send", creatorId);
            }
        } else {
            apiKey = defaultApiKey;
            accountId = defaultAccountId != null ? defaultAccountId.trim() : "";
            if (!accountId.isBlank() || (apiKey != null && !apiKey.isBlank()))
                log.debug("Using onlyfans.api.key and onlyfans.account.id from application.properties for send");
        }
        if (accountId == null || accountId.isBlank())
            throw new IllegalStateException("OnlyFans account ID is not set. Configure onlyfans.account.id in application.properties (or set onlyfans_account_id on the creator).");
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("OnlyFans API key is not set. Configure onlyfans.api.key in application.properties (or set onlyfans_api_key on the creator).");

        String accountIdSegment = accountId.trim();
        String url = String.format("%s/%s/chats/%s/messages", baseUrl.replaceAll("/+$", ""), accountIdSegment, chatId);
        log.debug("OnlyFans send message to chat {} (accountId segment present)", chatId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        OnlyFansSendMessageRequest request = new OnlyFansSendMessageRequest(sanitized, replyToMessageId);
        HttpEntity<OnlyFansSendMessageRequest> httpRequest = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, httpRequest, String.class
            );
            log.info("Successfully sent message to OnlyFans chat: {}", chatId);
        } catch (Exception e) {
            if (isSendToSelfError(e)) {
                log.warn("OnlyFans: cannot send message to chat {} — API returned 'Cannot send message to yourself'. Skipping send (e.g. creator unlocked own PPV).", chatId);
                throw new SendToSelfException(chatId, e);
            }
            if (isCannotMessageUserError(e)) {
                log.warn("OnlyFans: cannot send message to user (chat {}). User may have blocked, restricted DMs, or unsubscribed.", chatId);
                throw new CannotMessageUserException(chatId, e);
            }
            log.error("Failed to send message to OnlyFans chat: {}", chatId, e);
            errorLogService.logError(
                "ONLYFANS_SEND_MESSAGE_FAILED",
                "Failed to send message to chat: " + chatId,
                e,
                "Message: " + sanitized
            );
            throw new RuntimeException("Failed to send OnlyFans message", e);
        }
    }
    
    public void sendTypingIndicator(String chatId, String creatorId) {
        String apiKey;
        String accountId;
        
        if (creatorId != null && !creatorId.isBlank()) {
            Creator creator = creatorService.findByCreatorId(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found: " + creatorId));
            apiKey = creator.getOnlyfansApiKey();
            accountId = creator.getOnlyfansAccountId();
            if (apiKey == null || apiKey.isBlank()) apiKey = defaultApiKey;
            if (accountId == null || accountId.isBlank()) {
                accountId = defaultAccountId != null ? defaultAccountId.trim() : "";
                if (!accountId.isBlank())
                    log.warn("Creator {} has no onlyfans_account_id; using default for typing indicator", creatorId);
            }
        } else {
            apiKey = defaultApiKey;
            accountId = defaultAccountId != null ? defaultAccountId.trim() : "";
        }
        if (accountId == null || accountId.isBlank())
            throw new IllegalStateException("OnlyFans account ID is not set. Configure onlyfans.account.id in application.properties.");
        if (apiKey == null || apiKey.isBlank())
            return;

        String accountIdSegment = accountId.trim();
        String url = String.format("%s/%s/chats/%s/typing", baseUrl.replaceAll("/+$", ""), accountIdSegment, chatId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> httpRequest = new HttpEntity<>("{}", headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, httpRequest, String.class
            );
            log.info("Successfully sent typing indicator to OnlyFans chat: {}", chatId);
        } catch (Exception e) {
            log.warn("Failed to send typing indicator to OnlyFans chat: {} (non-critical)", chatId);
        }
    }

    private boolean isSendToSelfError(Throwable e) {
        String message = e != null ? e.getMessage() : null;
        if (message != null && message.contains("Cannot send message to yourself")) {
            return true;
        }
        if (e != null && e.getCause() != null) {
            return isSendToSelfError(e.getCause());
        }
        return false;
    }

    private boolean isCannotMessageUserError(Throwable e) {
        String message = e.getMessage();
        if (message != null && message.contains("Cannot send message to this user")) {
            return true;
        }
        if (e.getCause() != null) {
            return isCannotMessageUserError(e.getCause());
        }
        return false;
    }
}
