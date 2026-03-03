package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultMediaListResponse {
    private VaultData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VaultData {
        private List<VaultMediaItem> list;
        private Boolean hasMore;
    }
}
