package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultMediaItem {
    private Long id;
    private String type;
    private Boolean convertedToVideo;
    private Boolean canView;
    private Boolean hasError;
    private String createdAt;
    private Boolean isReady;
    private MediaFiles files;
    private Integer duration;
    private Boolean hasPosts;
    private List<ListState> listStates;
    private Boolean hasCustomPreview;
    private Map<String, String> videoSources;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaFiles {
        private MediaFile full;
        private MediaFile thumb;
        private MediaFile preview;
        private MediaFile squarePreview;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaFile {
        private String url;
        private Integer width;
        private Integer height;
        private Integer size;
        private List<Object> sources;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListState {
        private Long id;
        private String type;
        private String name;
        private Boolean hasMedia;
        private Boolean canAddMedia;
    }
}
