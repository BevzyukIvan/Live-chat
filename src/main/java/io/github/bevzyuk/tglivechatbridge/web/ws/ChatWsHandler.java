package io.github.bevzyuk.tglivechatbridge.web.ws;

import io.github.bevzyuk.tglivechatbridge.application.dto.WsInboundEvent;
import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import io.github.bevzyuk.tglivechatbridge.application.service.AdminMessageReceiptService;
import io.github.bevzyuk.tglivechatbridge.application.service.PendingAdminDeliveryService;
import io.github.bevzyuk.tglivechatbridge.infrastructure.store.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
public class ChatWsHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWsHandler.class);

    private final SessionRegistry sessions;
    private final PendingAdminDeliveryService pendingAdminDelivery;
    private final AdminMessageReceiptService receipts;
    private final ObjectMapper om;

    public ChatWsHandler(SessionRegistry sessions,
                         PendingAdminDeliveryService pendingAdminDelivery,
                         AdminMessageReceiptService receipts,
                         ObjectMapper om) {
        this.sessions = sessions;
        this.pendingAdminDelivery = pendingAdminDelivery;
        this.receipts = receipts;
        this.om = om;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String cid = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("cid");

        if (cid == null || cid.isBlank() || cid.length() > 64) {
            return session.close();
        }

        Flux<String> outbound = sessions.connect(cid)
                .map(this::toJson);

        String heartbeatJson = toJson(WsOutboundMessage.keepAlive());

        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(45))
                .map(t -> heartbeatJson);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> handleInbound(cid, payload))
                .then();

        Mono<Void> send = session.send(
                Flux.merge(outbound, heartbeat)
                        .map(session::textMessage)
        );

        Mono<Void> flushPending = pendingAdminDelivery.flushWhenClientConnected(cid);

        return Mono.when(inbound, send, flushPending)
                .doFinally(sig -> sessions.disconnect(cid));
    }

    private Mono<Void> handleInbound(String cid, String payload) {
        if (payload == null || payload.isBlank() || payload.length() > 2048) {
            return Mono.empty();
        }

        try {
            WsInboundEvent event = om.readValue(payload, WsInboundEvent.class);
            return receipts.handleClientEvent(cid, event);
        } catch (Exception e) {
            log.debug("Ignoring bad websocket inbound event for cid={}", cid, e);
            return Mono.empty();
        }
    }

    private String toJson(WsOutboundMessage msg) {
        try {
            return om.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"text\":\"serialization\"}";
        }
    }
}
