package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultListsResponse {
    private VaultListsData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VaultListsData {
        private List<VaultListInfo> list;
    }
}
