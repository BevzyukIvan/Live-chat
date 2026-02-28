package io.github.bevzyuk.tglivechatbridge.infrastructure.store;

import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import reactor.core.publisher.Flux;

public interface SessionRegistry {
    Flux<WsOutboundMessage> connect(String cid);
    void disconnect(String cid);
    void emit(String cid, WsOutboundMessage msg);
}
