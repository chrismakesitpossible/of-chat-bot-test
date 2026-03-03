package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyFansVaultListResponse {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("media_count")
    private Integer mediaCount;
    
    @JsonProperty("photos_count")
    private Integer photosCount;
    
    @JsonProperty("videos_count")
    private Integer videosCount;
    
    @JsonProperty("media")
    private List<MediaItem> media;
    
@Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaItem {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("duration")
        private Integer duration;

        @JsonProperty("preview")
        private String preview;

        @JsonProperty("thumb")
        private String thumb;
    }

}
