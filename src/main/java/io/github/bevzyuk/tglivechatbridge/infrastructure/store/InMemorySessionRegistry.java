package io.github.bevzyuk.tglivechatbridge.infrastructure.store;

import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InMemorySessionRegistry implements SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionRegistry.class);

    private static final int BACKPRESSURE_BUFFER = 256;

    private static final class Entry {
        final Sinks.Many<WsOutboundMessage> sink;
        final AtomicInteger connections = new AtomicInteger(0);

        Entry() {
            this.sink = Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_BUFFER);
        }
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    @Override
    public Flux<WsOutboundMessage> connect(String cid) {
        AtomicReference<Entry> ref = new AtomicReference<>();

        sessions.compute(cid, (key, existing) -> {
            Entry e = (existing != null) ? existing : new Entry();
            e.connections.incrementAndGet();
            ref.set(e);
            return e;
        });

        Entry e = ref.get();
        if (e == null) {
            return Flux.error(new IllegalStateException("Session entry was not created for cid=" + cid));
        }

        return e.sink.asFlux();
    }

    @Override
    public void disconnect(String cid) {
        AtomicReference<Entry> toComplete = new AtomicReference<>();

        sessions.computeIfPresent(cid, (key, e) -> {
            int left = e.connections.decrementAndGet();

            if (left <= 0) {
                if (left < 0) {
                    log.warn("connections < 0 for cid={}, left={}", cid, left);
                }
                toComplete.set(e);
                return null;
            }

            return e;
        });

        Entry e = toComplete.get();
        if (e != null) {
            Sinks.EmitResult r = e.sink.tryEmitComplete();
            if (r.isFailure()
                    && r != Sinks.EmitResult.FAIL_TERMINATED
                    && r != Sinks.EmitResult.FAIL_CANCELLED) {
                log.debug("sink.complete failed for cid={}, result={}", cid, r);
            }
        }
    }

    @Override
    public void emit(String cid, WsOutboundMessage msg) {
        Entry e = sessions.get(cid);
        if (e == null) {
            return;
        }

        Sinks.EmitResult r = e.sink.tryEmitNext(msg);
        if (r.isFailure()) {
            if (r == Sinks.EmitResult.FAIL_TERMINATED || r == Sinks.EmitResult.FAIL_CANCELLED) {
                sessions.remove(cid, e);
            }

            if (r != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.debug("sink.emit failed for cid={}, result={}", cid, r);
            }
        }
    }

    public int activeCidCount() {
        return sessions.size();
    }
}