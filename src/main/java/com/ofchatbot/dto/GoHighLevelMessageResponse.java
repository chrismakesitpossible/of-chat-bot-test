package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoHighLevelMessageResponse {
    private MessageContainer messages;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageContainer {
        private List<MessageData> messages;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageData {
        private String body;
        private String direction;
        private String dateAdded;
    }
}
