package com.ofchatbot.service;

import com.ofchatbot.dto.GoHighLevelConversationResponse;
import com.ofchatbot.dto.GoHighLevelMessageResponse;
import com.ofchatbot.dto.WebhookPayload;
import com.ofchatbot.entity.Fan;
import com.ofchatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramChatbotService {
    
    private final FanService fanService;
    private final MessageService messageService;
    private final GoHighLevelService goHighLevelService;
    private final AnthropicService anthropicService;
    
    public void processIncomingMessage(WebhookPayload payload) {
        log.info("Processing incoming message from contact: {}", payload.getContactId());
        
        Fan fan = fanService.createOrUpdateFan(
            payload.getCreatorId(),
            payload.getContactId(),
            payload.getIgUsername()
        );
        
        GoHighLevelConversationResponse conversationResponse = goHighLevelService.searchConversations(
            payload.getContactId(),
            payload.getLocationId()
        );
        
        if (conversationResponse.getConversations() == null || conversationResponse.getConversations().isEmpty()) {
            log.error("No conversation found for contact: {}", payload.getContactId());
            return;
        }
        
        GoHighLevelConversationResponse.ConversationData conversation = conversationResponse.getConversations().get(0);
        
        messageService.saveMessage(
            payload.getCreatorId(),
            payload.getContactId(),
            "user",
            payload.getMessageText(),
            payload.getTimestamp()
        );
        
        List<Message> conversationHistory = getFormattedConversationHistory(
            conversation.getId(),
            payload.getContactId(),
            payload.getLocationId()
        );
        
        String aiResponse = anthropicService.generateResponse(conversationHistory, payload.getMessageText());
        
        try {
            Thread.sleep(25000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wait interrupted", e);
        }
        
        if ("instagram".equalsIgnoreCase(payload.getPlatform())) {
            goHighLevelService.sendMessage(
                conversation.getId(),
                payload.getContactId(),
                conversation.getLocationId(),
                aiResponse,
                "IG"
            );
        } else {
            goHighLevelService.sendMessage(
                conversation.getId(),
                payload.getContactId(),
                conversation.getLocationId(),
                aiResponse,
                "FB"
            );
        }
        
        messageService.saveMessage(
            payload.getCreatorId(),
            payload.getContactId(),
            "bot",
            aiResponse,
            payload.getTimestamp()
        );
        
        log.info("Successfully processed message for contact: {}", payload.getContactId());
    }
    
    private List<Message> getFormattedConversationHistory(String conversationId, String contactId, String locationId) {
        try {
            GoHighLevelMessageResponse messageResponse = goHighLevelService.getConversationMessages(
                conversationId, contactId, locationId
            );
            
            if (messageResponse.getMessages() == null || messageResponse.getMessages().getMessages() == null) {
                return messageService.getRecentMessages(contactId, 10);
            }
            
            List<GoHighLevelMessageResponse.MessageData> ghlMessages = messageResponse.getMessages().getMessages();
            ghlMessages.sort((a, b) -> a.getDateAdded().compareTo(b.getDateAdded()));
            
            int startIndex = Math.max(0, ghlMessages.size() - 10);
            List<GoHighLevelMessageResponse.MessageData> recentMessages = ghlMessages.subList(startIndex, ghlMessages.size());
            
            List<Message> formattedMessages = new ArrayList<>();
            for (GoHighLevelMessageResponse.MessageData msg : recentMessages) {
                Message message = new Message();
                message.setContactId(contactId);
                message.setRole("inbound".equals(msg.getDirection()) ? "user" : "bot");
                message.setText(msg.getBody());
                formattedMessages.add(message);
            }
            
            return formattedMessages;
        } catch (Exception e) {
            log.warn("Failed to get GHL messages, falling back to database", e);
            return messageService.getRecentMessages(contactId, 10);
        }
    }
}
