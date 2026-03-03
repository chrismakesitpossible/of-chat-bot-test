package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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
