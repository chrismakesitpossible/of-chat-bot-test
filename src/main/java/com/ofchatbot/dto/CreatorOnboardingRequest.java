package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatorOnboardingRequest {
    private String name;
    private String onlyfansUrl;
    private String onlyfansApiKey;
    private String onlyfansAccountId;
    private String tone;
    private String trackingCode;
}
