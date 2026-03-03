package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyFansAllVaultMediaResponse {
    
    @JsonProperty("list")
    private List<MediaItem> mediaList;
    
    @JsonProperty("hasMore")
    private Boolean hasMore;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaItem {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("url")
        private String url;
        
        @JsonProperty("preview")
        private String preview;
        
        @JsonProperty("duration")
        private Integer duration;
        
        @JsonProperty("hasMedia")
        private Boolean hasMedia;
        
        @JsonProperty("listStates")
        private ListStates listStates;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListStates {
        @JsonProperty("posts")
        private Boolean posts;
        
        @JsonProperty("stories")
        private Boolean stories;
        
        @JsonProperty("messages")
        private Boolean messages;
        
        @JsonProperty("streams")
        private Boolean streams;
        
        @JsonProperty("custom")
        private Boolean custom;
        
        @JsonProperty("media_stickers")
        private Boolean mediaStickers;
    }
}
