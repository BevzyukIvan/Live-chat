package io.github.bevzyuk.tglivechatbridge.application.dto;

public record WsOutboundMessage(
        String type,
        String text,
        String messageId,
        Long threadId
) {
    public static WsOutboundMessage msg(String messageId, String text, Long threadId) {
        return new WsOutboundMessage("MSG", text, messageId, threadId);
    }

    public static WsOutboundMessage keepAlive() {
        return new WsOutboundMessage("KEEPALIVE", "", null, null);
    }
}
