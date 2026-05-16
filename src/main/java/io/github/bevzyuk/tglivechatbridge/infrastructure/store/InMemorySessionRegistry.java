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
        final Object emitLock = new Object();

        Entry() {
            this.sink = Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_BUFFER);
        }
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    @Override
    public Flux<WsOutboundMessage> connect(String cid) {
        if (cid == null || cid.isBlank()) {
            return Flux.error(new IllegalArgumentException("cid must not be blank"));
        }

        Entry e = sessions.compute(cid, (key, existing) -> {
            Entry entry = (existing != null) ? existing : new Entry();
            entry.connections.incrementAndGet();
            return entry;
        });

        return e.sink.asFlux();
    }

    @Override
    public void disconnect(String cid) {
        if (cid == null || cid.isBlank()) {
            return;
        }

        AtomicReference<Entry> toComplete = new AtomicReference<>();

        sessions.computeIfPresent(cid, (key, e) -> {
            int left = e.connections.decrementAndGet();

            if (left <= 0) {
                toComplete.set(e);
                return null;
            }

            return e;
        });

        Entry e = toComplete.get();
        if (e != null) {
            synchronized (e.emitLock) {
                Sinks.EmitResult r = e.sink.tryEmitComplete();

                if (!r.isSuccess()
                        && r != Sinks.EmitResult.FAIL_TERMINATED
                        && r != Sinks.EmitResult.FAIL_CANCELLED) {
                    log.debug("sink.complete failed for cid={}, result={}", cid, r);
                }
            }
        }
    }

    @Override
    public boolean emit(String cid, WsOutboundMessage msg) {
        if (cid == null || cid.isBlank() || msg == null) {
            return false;
        }

        Entry e = sessions.get(cid);
        if (e == null) {
            return false;
        }

        synchronized (e.emitLock) {
            if (sessions.get(cid) != e || e.connections.get() <= 0) {
                return false;
            }

            Sinks.EmitResult r = e.sink.tryEmitNext(msg);
            if (r.isSuccess()) {
                return true;
            }

            if (r == Sinks.EmitResult.FAIL_TERMINATED || r == Sinks.EmitResult.FAIL_CANCELLED) {
                sessions.remove(cid, e);
            }

            if (r != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.debug("sink.emit failed for cid={}, result={}", cid, r);
            }

            return false;
        }
    }

    @Override
    public boolean isOnline(String cid) {
        if (cid == null || cid.isBlank()) {
            return false;
        }

        Entry e = sessions.get(cid);
        return e != null && e.connections.get() > 0;
    }

    public int activeCidCount() {
        return sessions.size();
    }
}
