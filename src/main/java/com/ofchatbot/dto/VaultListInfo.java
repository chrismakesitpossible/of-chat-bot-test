package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultListInfo {
    private Long id;
    private String name;
    private Integer photosCount;
    private Integer videosCount;
}
