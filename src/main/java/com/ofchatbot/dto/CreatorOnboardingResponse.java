package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatorOnboardingResponse {
    private boolean success;
    private String message;
    private String creatorId;
    private String webhookUrl;
}
