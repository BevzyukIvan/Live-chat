package io.github.bevzyuk.tglivechatbridge.web.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        String text,
        @JsonProperty("message_thread_id") Long messageThreadId
) { }