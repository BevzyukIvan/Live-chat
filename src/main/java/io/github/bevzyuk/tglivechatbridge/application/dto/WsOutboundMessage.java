package io.github.bevzyuk.tglivechatbridge.application.dto;

public record WsOutboundMessage(String type, String text) {
    public static WsOutboundMessage msg(String text) {
        return new WsOutboundMessage("MSG", text);
    }
    public static WsOutboundMessage keepAlive() {
        return new WsOutboundMessage("KEEPALIVE", "");
    }
}
