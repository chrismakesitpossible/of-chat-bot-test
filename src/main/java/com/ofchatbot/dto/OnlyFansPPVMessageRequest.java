package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyFansPPVMessageRequest {
    
    @JsonProperty("text")
    private String text;
    
    @JsonProperty("price")
    private Integer price;
    
    /**
     * Provider docs use `mediaFiles` for PPV media attachments.
     */
    @JsonProperty("mediaFiles")
    private List<String> mediaFiles;

    /**
     * Backwards/alternate field used by some wrappers.
     */
    @JsonProperty("media_ids")
    private List<String> mediaIds;

    /**
     * Optional preview media visible for free (must also be in mediaFiles).
     */
    @JsonProperty("previews")
    private List<String> previews;
    
    @JsonProperty("reply_to_message_id")
    private String replyToMessageId;
}
