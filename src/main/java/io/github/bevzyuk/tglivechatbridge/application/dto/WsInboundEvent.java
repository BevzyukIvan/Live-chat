package io.github.bevzyuk.tglivechatbridge.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WsInboundEvent(
        String type,
        String messageId,
        Long threadId,
        Long ts
) { }
