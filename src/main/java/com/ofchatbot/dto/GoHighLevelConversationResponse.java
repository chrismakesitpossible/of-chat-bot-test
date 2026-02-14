package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoHighLevelConversationResponse {
    private List<ConversationData> conversations;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationData {
        private String id;
        private String locationId;
        private String contactId;
    }
}
