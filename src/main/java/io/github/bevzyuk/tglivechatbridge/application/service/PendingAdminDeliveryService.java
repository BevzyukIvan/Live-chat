package io.github.bevzyuk.tglivechatbridge.application.service;

import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import io.github.bevzyuk.tglivechatbridge.config.props.TelegramProperties;
import io.github.bevzyuk.tglivechatbridge.infrastructure.store.SessionRegistry;
import io.github.bevzyuk.tglivechatbridge.infrastructure.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PendingAdminDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PendingAdminDeliveryService.class);

    private static final Duration DEFAULT_GRACE = Duration.ofSeconds(10);
    private static final Duration CONNECT_FLUSH_DELAY = Duration.ofMillis(150);
    private static final int MAX_PENDING_MESSAGES_PER_CID = 50;

    private final SessionRegistry sessions;
    private final TelegramClient telegram;
    private final AdminMessageReceiptService receipts;
    private final Duration offlineDeliveryGrace;
    private final Map<String, PendingBatch> pendingByCid = new ConcurrentHashMap<>();

    public PendingAdminDeliveryService(SessionRegistry sessions,
                                       TelegramClient telegram,
                                       TelegramProperties props,
                                       AdminMessageReceiptService receipts) {
        this.sessions = sessions;
        this.telegram = telegram;
        this.receipts = receipts;
        this.offlineDeliveryGrace = normalizeGrace(props.offlineDeliveryGrace());
    }

    public Mono<Void> deliverOrQueue(String cid, long threadId, String text) {
        String normalizedText = normalizeText(text);
        if (cid == null || cid.isBlank() || normalizedText == null) {
            return Mono.empty();
        }

        PendingAdminMessage message = new PendingAdminMessage(
                receipts.register(cid, threadId, normalizedText),
                normalizedText,
                threadId
        );

        boolean acceptedBySocket = sessions.emit(
                cid,
                WsOutboundMessage.msg(message.messageId(), message.text(), message.threadId())
        );

        if (acceptedBySocket) {
            return Mono.empty();
        }

        AtomicBoolean stored = new AtomicBoolean(false);
        PendingBatch batch = pendingByCid.compute(cid, (key, existing) -> {
            PendingBatch target = existing != null ? existing : new PendingBatch(threadId);
            stored.set(target.add(message));
            return target;
        });

        if (!stored.get()) {
            receipts.forget(message.messageId());
        }

        if (batch.markTimerStarted()) {
            scheduleExpiration(cid, batch);
        }

        return Mono.empty();
    }

    public Mono<Void> flushWhenClientConnected(String cid) {
        if (cid == null || cid.isBlank()) {
            return Mono.empty();
        }

        return Mono.delay(CONNECT_FLUSH_DELAY)
                .then(flushPendingToClient(cid))
                .onErrorResume(e -> {
                    log.warn("Failed to flush pending admin messages for cid={}", cid, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> flushPendingToClient(String cid) {
        PendingBatch batch = pendingByCid.remove(cid);
        if (batch == null) {
            return Mono.empty();
        }

        PendingSnapshot snapshot = batch.snapshot();
        if (snapshot.messages().isEmpty()) {
            return notifyAboutDroppedMessagesIfNeeded(snapshot, batch.threadId());
        }

        int acceptedBySocket = 0;
        List<PendingAdminMessage> failedMessages = new ArrayList<>();

        for (PendingAdminMessage message : snapshot.messages()) {
            if (sessions.emit(cid, WsOutboundMessage.msg(message.messageId(), message.text(), message.threadId()))) {
                acceptedBySocket++;
            } else {
                failedMessages.add(message);
            }
        }

        forgetReceipts(failedMessages);

        int failed = snapshot.messages().size() - acceptedBySocket + snapshot.droppedMessages();
        if (failed <= 0) {
            return Mono.empty();
        }

        return telegram.sendMessage(buildNotDeliveredText(failed), batch.threadId());
    }

    private void scheduleExpiration(String cid, PendingBatch batch) {
        Mono.delay(offlineDeliveryGrace)
                .flatMap(ignored -> expirePendingBatch(cid, batch))
                .doOnError(e -> log.warn("Pending admin delivery expiration failed for cid={}", cid, e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> expirePendingBatch(String cid, PendingBatch batch) {
        boolean removed = pendingByCid.remove(cid, batch);
        if (!removed) {
            return Mono.empty();
        }

        PendingSnapshot snapshot = batch.snapshot();
        if (snapshot.messages().isEmpty()) {
            return notifyAboutDroppedMessagesIfNeeded(snapshot, batch.threadId());
        }

        if (sessions.isOnline(cid)) {
            int acceptedBySocket = 0;
            List<PendingAdminMessage> failedMessages = new ArrayList<>();

            for (PendingAdminMessage message : snapshot.messages()) {
                if (sessions.emit(cid, WsOutboundMessage.msg(message.messageId(), message.text(), message.threadId()))) {
                    acceptedBySocket++;
                } else {
                    failedMessages.add(message);
                }
            }

            forgetReceipts(failedMessages);

            int failed = snapshot.messages().size() - acceptedBySocket + snapshot.droppedMessages();
            if (failed <= 0) {
                return Mono.empty();
            }

            return telegram.sendMessage(buildNotDeliveredText(failed), batch.threadId());
        }

        forgetReceipts(snapshot.messages());

        return telegram.sendMessage(
                buildNotDeliveredText(snapshot.messages().size() + snapshot.droppedMessages()),
                batch.threadId()
        );
    }

    private void forgetReceipts(List<PendingAdminMessage> messages) {
        for (PendingAdminMessage message : messages) {
            receipts.forget(message.messageId());
        }
    }

    private Mono<Void> notifyAboutDroppedMessagesIfNeeded(PendingSnapshot snapshot, long threadId) {
        if (snapshot.droppedMessages() <= 0) {
            return Mono.empty();
        }
        return telegram.sendMessage(buildNotDeliveredText(snapshot.droppedMessages()), threadId);
    }

    private String buildNotDeliveredText(int count) {
        if (count <= 1) {
            return "⚠️ Клієнт уже покинув сторінку. Повідомлення не доставлено.";
        }

        return "⚠️ Клієнт уже покинув сторінку. Не доставлено повідомлень: " + count + ".";
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static Duration normalizeGrace(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            return DEFAULT_GRACE;
        }

        if (value.compareTo(Duration.ofSeconds(2)) < 0) {
            return Duration.ofSeconds(2);
        }

        if (value.compareTo(Duration.ofMinutes(1)) > 0) {
            return Duration.ofMinutes(1);
        }

        return value;
    }

    private record PendingAdminMessage(String messageId, String text, long threadId) { }

    private record PendingSnapshot(List<PendingAdminMessage> messages, int droppedMessages) { }

    private static final class PendingBatch {
        private final long threadId;
        private final List<PendingAdminMessage> messages = new ArrayList<>();
        private final AtomicBoolean timerStarted = new AtomicBoolean(false);
        private int droppedMessages;

        private PendingBatch(long threadId) {
            this.threadId = threadId;
        }

        long threadId() {
            return threadId;
        }

        synchronized boolean add(PendingAdminMessage message) {
            Objects.requireNonNull(message, "message");

            if (messages.size() >= MAX_PENDING_MESSAGES_PER_CID) {
                droppedMessages++;
                return false;
            }

            messages.add(message);
            return true;
        }

        synchronized PendingSnapshot snapshot() {
            return new PendingSnapshot(List.copyOf(messages), droppedMessages);
        }

        boolean markTimerStarted() {
            return timerStarted.compareAndSet(false, true);
        }
    }
}
