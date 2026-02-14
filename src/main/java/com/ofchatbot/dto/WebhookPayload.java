package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {
    private String body;
    private String timestamp;
    private String creatorId;
    private String contactId;
    private String igUsername;
    private String messageText;
    private String platform;
    private String direction;
    private String conversationId;
    private String locationId;
}
