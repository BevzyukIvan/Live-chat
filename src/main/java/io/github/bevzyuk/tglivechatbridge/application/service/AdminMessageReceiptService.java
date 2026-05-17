package io.github.bevzyuk.tglivechatbridge.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.github.bevzyuk.tglivechatbridge.application.dto.WsInboundEvent;
import io.github.bevzyuk.tglivechatbridge.infrastructure.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AdminMessageReceiptService {

    private static final Logger log = LoggerFactory.getLogger(AdminMessageReceiptService.class);

    private static final Duration RECEIPT_TTL = Duration.ofHours(6);
    private static final int MAX_RECEIPTS = 10_000;

    private final TelegramClient telegram;
    private final Cache<String, AdminMessageStatus> statuses = Caffeine.newBuilder()
            .expireAfterWrite(RECEIPT_TTL)
            .maximumSize(MAX_RECEIPTS)
            .scheduler(Scheduler.systemScheduler())
            .build();

    public AdminMessageReceiptService(TelegramClient telegram) {
        this.telegram = telegram;
    }

    public String register(String cid, long threadId, String text) {
        if (cid == null || cid.isBlank()) {
            throw new IllegalArgumentException("cid must not be blank");
        }

        String messageId = newMessageId();
        statuses.put(messageId, new AdminMessageStatus(cid, threadId, messageId, normalizePreview(text)));
        return messageId;
    }

    public void forget(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        statuses.invalidate(messageId);
    }

    public Mono<Void> handleClientEvent(String cid, WsInboundEvent event) {
        if (cid == null || cid.isBlank() || event == null || event.type() == null) {
            return Mono.empty();
        }

        String type = event.type().trim().toUpperCase(Locale.ROOT);

        return switch (type) {
            case "MSG_DELIVERED", "DELIVERED" -> markDelivered(cid, event.messageId());
            case "MSG_READ", "READ" -> markRead(cid, event.messageId());
            default -> Mono.empty();
        };
    }

    private Mono<Void> markDelivered(String cid, String messageId) {
        AdminMessageStatus status = findStatus(cid, messageId);
        if (status == null) {
            return Mono.empty();
        }

        if (!status.delivered.compareAndSet(false, true)) {
            return Mono.empty();
        }

        return telegram.sendMessage(buildDeliveredText(status), status.threadId)
                .onErrorResume(e -> {
                    log.warn("Failed to send delivered receipt for cid={}, messageId={}", cid, messageId, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> markRead(String cid, String messageId) {
        AdminMessageStatus status = findStatus(cid, messageId);
        if (status == null) {
            return Mono.empty();
        }

        boolean shouldSendDelivered = status.delivered.compareAndSet(false, true);
        boolean shouldSendRead = status.read.compareAndSet(false, true);

        if (!shouldSendRead) {
            return Mono.empty();
        }

        Mono<Void> deliveredNotice = shouldSendDelivered
                ? telegram.sendMessage(buildDeliveredText(status), status.threadId)
                : Mono.empty();

        Mono<Void> readNotice = telegram.sendMessage(buildReadText(status), status.threadId);

        return deliveredNotice
                .then(readNotice)
                .doOnSuccess(ignored -> statuses.invalidate(messageId))
                .onErrorResume(e -> {
                    log.warn("Failed to send read receipt for cid={}, messageId={}", cid, messageId, e);
                    return Mono.empty();
                });
    }

    private AdminMessageStatus findStatus(String cid, String messageId) {
        if (cid == null || cid.isBlank() || messageId == null || messageId.isBlank()) {
            return null;
        }

        AdminMessageStatus status = statuses.getIfPresent(messageId);
        if (status == null) {
            return null;
        }

        if (!cid.equals(status.cid)) {
            log.debug("Ignoring receipt with wrong cid. expected={}, actual={}, messageId={}", status.cid, cid, messageId);
            return null;
        }

        return status;
    }

    private String buildDeliveredText(AdminMessageStatus status) {
        return "\uD83D\uDE09 Повідомлення доставлено на сайт клієнта."
                + buildMessageDetails(status);
    }

    private String buildReadText(AdminMessageStatus status) {
        return "👁 Повідомлення прочитано клієнтом у чаті."
                + buildMessageDetails(status);
    }

    private String buildMessageDetails(AdminMessageStatus status) {
        StringBuilder sb = new StringBuilder();

        if (status.preview != null) {
            sb.append("\n").append(status.preview);
        }

        return sb.toString();
    }

    private static String normalizePreview(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120) + "…";
        }

        return "Текст повідомлення: «" + normalized + "»";
    }

    private static String newMessageId() {
        return "m_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class AdminMessageStatus {
        private final String cid;
        private final long threadId;
        private final String preview;
        private final AtomicBoolean delivered = new AtomicBoolean(false);
        private final AtomicBoolean read = new AtomicBoolean(false);

        private AdminMessageStatus(String cid, long threadId, String messageId, String preview) {
            this.cid = cid;
            this.threadId = threadId;
            this.preview = preview;
        }
    }
}
