package io.github.bevzyuk.tglivechatbridge.application.dto;

public record ChatSendResponse(
        String cid,
        long threadId
) { }
