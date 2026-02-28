package io.github.bevzyuk.tglivechatbridge.web.ws;


import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import io.github.bevzyuk.tglivechatbridge.infrastructure.store.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
public class ChatWsHandler implements WebSocketHandler {

    private final SessionRegistry sessions;
    private final ObjectMapper om;

    public ChatWsHandler(SessionRegistry sessions, ObjectMapper om) {
        this.sessions = sessions;
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
                .map(msg -> {
                    try {
                        return om.writeValueAsString(msg);
                    } catch (Exception e) {
                        return "{\"type\":\"ERROR\",\"text\":\"serialization\"}";
                    }
                });

        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(t -> {
                    try {
                        return om.writeValueAsString(WsOutboundMessage.keepAlive());
                    } catch (Exception e) {
                        return "{\"type\":\"KEEPALIVE\",\"text\":\"\"}";
                    }
                });

        Mono<Void> inbound = session.receive().then();

        Mono<Void> send = session.send(
                Flux.merge(outbound, heartbeat).map(session::textMessage)
        );

        return Mono.when(inbound, send)
                .doFinally(sig -> sessions.disconnect(cid));
    }
}
