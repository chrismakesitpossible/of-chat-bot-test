package com.ofchatbot.service;

import com.ofchatbot.entity.Message;
import com.ofchatbot.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    
    @Transactional
    public Message saveMessage(String creatorId, String contactId, String role, String text, String timestamp, String platform) {
        Message message = new Message();
        message.setCreatorId(creatorId);
        message.setContactId(contactId);
        message.setRole(role);
        message.setText(text);
        message.setPlatform(platform);
        message.setTimestamp(parseTimestamp(timestamp));
        message.setCreatedAt(LocalDateTime.now());
        
        Message saved = messageRepository.save(message);
        log.info("Saved {} message for contact: {} on platform: {}", role, contactId, platform);
        return saved;
    }
    
    @Transactional
    public Message saveMessage(String creatorId, String contactId, String role, String text, String timestamp) {
        return saveMessage(creatorId, contactId, role, text, timestamp, "instagram");
    }
    
    public List<Message> getConversationHistory(String contactId) {
        return messageRepository.findByContactIdOrderByTimestampAsc(contactId);
    }
    
    public List<Message> getRecentMessages(String contactId, int limit) {
        List<Message> messages = messageRepository.findTop10ByContactIdOrderByTimestampDesc(contactId);
        messages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        return messages;
    }
    public String getConversationHistory(String contactId, int limit) {
        List<Message> messages = getRecentMessages(contactId, limit);

        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        for (Message msg : messages) {
            String role = msg.getRole().equals("user") ? "Fan" : "You";
            history.append(role).append(": ").append(msg.getText()).append("\n");
        }

        return history.toString().trim();
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            if (timestamp.contains(":") && timestamp.length() <= 5) {
                return LocalDateTime.now().withHour(Integer.parseInt(timestamp.split(":")[0]))
                        .withMinute(Integer.parseInt(timestamp.split(":")[1]));
            }
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }
}
