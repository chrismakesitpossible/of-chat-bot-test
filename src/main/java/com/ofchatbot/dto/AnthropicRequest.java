package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicRequest {
    private String model;
    private Integer max_tokens;
    private List<MessageContent> messages;
    private String system;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageContent {
        private String role;
        private String content;
    }
}
