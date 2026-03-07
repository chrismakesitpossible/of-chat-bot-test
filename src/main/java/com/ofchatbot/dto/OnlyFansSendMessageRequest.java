package com.ofchatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnlyFansSendMessageRequest {
    private String text;
    private String replyTo;
    /** Creator tag(s) / release-form tag — required by OnlyFans API. */
    private List<Long> rfTag;
}
