package com.ofchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofchatbot.entity.Message;
import com.ofchatbot.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageRepository messageRepository;
    
    @Transactional
    public Message saveMessage(String creatorId, String contactId, String role, String text, String timestamp, String platform) {
        return saveMessage(creatorId, contactId, role, text, timestamp, platform, null);
    }

    @Transactional
    public Message saveMessage(String creatorId, String contactId, String role, String text, String timestamp, String platform, String externalMessageId) {
        Message message = new Message();
        message.setCreatorId(creatorId);
        message.setContactId(contactId);
        message.setRole(role);
        message.setText(text);
        message.setPlatform(platform);
        message.setTimestamp(parseTimestamp(timestamp));
        message.setCreatedAt(LocalDateTime.now());
        message.setExternalMessageId(externalMessageId);

        Message saved = messageRepository.save(message);
        log.info("Saved {} message for contact: {} on platform: {}", role, contactId, platform);
        return saved;
    }
    
    @Transactional
    public Message saveMessage(String creatorId, String contactId, String role, String text, String timestamp) {
        return saveMessage(creatorId, contactId, role, text, timestamp, "instagram");
    }
    
    public String getConversationHistory(String contactId, int limit) {
            List<Message> messages = getRecentMessages(contactId, limit);

            if (messages.isEmpty()) {
                return "";
            }

            StringBuilder history = new StringBuilder();
            Message previousMessage = null;
            int consecutiveFanMessages = 0;

            for (Message msg : messages) {
                String role = msg.getRole().equals("user") ? "Fan" : "You";

                // Combine consecutive fan messages sent within 2 minutes into one entry
                if (previousMessage != null && 
                    msg.getRole().equals("user") && 
                    previousMessage.getRole().equals("user") &&
                    java.time.Duration.between(previousMessage.getTimestamp(), msg.getTimestamp()).toMinutes() < 2) {

                    // Append to the last line with line break to preserve message structure
                    history.append("\n").append(msg.getText());
                    consecutiveFanMessages++;
                } else {
                    // Start new line for this message
                    if (history.length() > 0) {
                        history.append("\n");
                    }

                    // Add note if previous messages were batched
                    if (consecutiveFanMessages > 0) {
                        history.append(" [sent ").append(consecutiveFanMessages + 1).append(" messages quickly]\n");
                        consecutiveFanMessages = 0;
                    }

                    history.append(role).append(": ").append(msg.getText());
                }

                previousMessage = msg;
            }

            // Add note at the end if last messages were batched
            if (consecutiveFanMessages > 0) {
                history.append(" [sent ").append(consecutiveFanMessages + 1).append(" messages quickly]");
            }

            return history.toString().trim();
        }

    public String getLastFanMessageId(String contactId) {
        List<Message> messages = messageRepository.findTop10ByContactIdOrderByTimestampDesc(contactId);
        return messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .filter(m -> m.getExternalMessageId() != null)
                .findFirst()
                .map(Message::getExternalMessageId)
                .orElse(null);
    }

    
    public List<Message> getRecentMessages(String contactId, int limit) {
        List<Message> messages = messageRepository.findTop10ByContactIdOrderByTimestampDesc(contactId);
        messages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        return messages;
    }

    /** Last N bot messages for this contact (newest first). Used for "replied within X min" active detection. */
    public List<Message> getLastBotMessages(String contactId, int n) {
        List<Message> messages = messageRepository.findTop10ByContactIdOrderByTimestampDesc(contactId);
        return messages.stream()
            .filter(m -> "bot".equals(m.getRole()))
            .limit(n)
            .toList();
    }
    
    /**
     * Import past chat messages from the OF API response JSON.
     * Called once per fan to backfill conversation history from before the webhook was set up.
     */
    @Transactional
    public int importChatHistory(String chatHistoryJson, String creatorId, String contactId, String accountId) {
        if (chatHistoryJson == null || chatHistoryJson.isBlank()) return 0;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(chatHistoryJson);

            // The API may return { "data": [...] } or just [...]
            JsonNode messagesNode = root.isArray() ? root : (root.has("data") ? root.get("data") : root);
            if (!messagesNode.isArray()) {
                log.warn("Chat history response is not an array — skipping import for contact {}", contactId);
                return 0;
            }

            List<Message> toSave = new ArrayList<>();
            for (JsonNode msg : messagesNode) {
                String text = msg.has("text") ? msg.get("text").asText("") : "";
                if (text.isBlank()) continue;

                // Strip HTML tags
                text = text.replaceAll("<[^>]*>", "").trim();
                if (text.isBlank()) continue;

                String externalId = msg.has("id") ? msg.get("id").asText(null) : null;

                // Skip if already imported
                if (externalId != null && messageRepository.findByExternalMessageId(externalId).isPresent()) {
                    continue;
                }

                // Determine role: if fromUser.id == accountId → bot, otherwise → user
                String role = "user";
                if (msg.has("fromUser") && msg.get("fromUser").has("id")) {
                    String fromId = msg.get("fromUser").get("id").asText("");
                    if (fromId.equals(accountId)) {
                        role = "bot";
                    }
                }

                String createdAt = msg.has("createdAt") ? msg.get("createdAt").asText(null) : null;

                Message message = new Message();
                message.setCreatorId(creatorId);
                message.setContactId(contactId);
                message.setRole(role);
                message.setText(text);
                message.setPlatform("onlyfans");
                message.setTimestamp(createdAt != null ? parseTimestamp(createdAt) : LocalDateTime.now());
                message.setCreatedAt(LocalDateTime.now());
                message.setExternalMessageId(externalId);
                toSave.add(message);
            }

            if (!toSave.isEmpty()) {
                messageRepository.saveAll(toSave);
                log.info("Imported {} past messages for contact {}", toSave.size(), contactId);
            }
            return toSave.size();

        } catch (Exception e) {
            log.warn("Failed to import chat history for contact {}: {}", contactId, e.getMessage());
            return 0;
        }
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
