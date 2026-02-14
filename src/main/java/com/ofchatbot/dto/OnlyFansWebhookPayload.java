package com.ofchatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyFansWebhookPayload {
    private String event;
    private String account_id;
    private PayloadData payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayloadData {
        private Long id;
        private String text;
        private String createdAt;
        private FromUser fromUser;
        private FanData fanData;
        private User user;
        private String user_id;
        private String description;
        private Double amount;
        private Double amountGross;
        private Double amountNet;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FromUser {
            private Long id;
            private String name;
            private String username;
            private String avatar;
            private Boolean subscribedOn;
            private String subscribedOnDuration;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class User {
            private Long id;
            private String name;
            private String username;
            private String avatar;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FanData {
            private Boolean available;
            private String last_updated_at;
            private Spending spending;
            
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Spending {
                private Double total;
                private Double messages;
                private Double subscribes;
                private Double posts;
                private Double tips;
                private Double streams;
            }
        }
    }
}
