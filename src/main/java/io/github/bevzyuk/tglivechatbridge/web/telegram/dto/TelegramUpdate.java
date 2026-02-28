package io.github.bevzyuk.tglivechatbridge.web.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        TelegramMessage message
) { }