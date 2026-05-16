package io.github.bevzyuk.tglivechatbridge.infrastructure.store;

import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import reactor.core.publisher.Flux;

public interface SessionRegistry {
    Flux<WsOutboundMessage> connect(String cid);

    void disconnect(String cid);

    /**
     * @return true if the message was accepted by an active WebSocket session.
     */
    boolean emit(String cid, WsOutboundMessage msg);

    boolean isOnline(String cid);
}
