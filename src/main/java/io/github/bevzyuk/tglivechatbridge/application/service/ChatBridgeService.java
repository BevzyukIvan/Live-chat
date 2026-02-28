package io.github.bevzyuk.tglivechatbridge.application.service;

import io.github.bevzyuk.tglivechatbridge.application.dto.ChatSendRequest;
import io.github.bevzyuk.tglivechatbridge.application.dto.WsOutboundMessage;
import io.github.bevzyuk.tglivechatbridge.infrastructure.store.SessionRegistry;
import io.github.bevzyuk.tglivechatbridge.infrastructure.store.TelegramTopicStore;
import io.github.bevzyuk.tglivechatbridge.infrastructure.telegram.TelegramClient;
import io.github.bevzyuk.tglivechatbridge.web.telegram.dto.TelegramUpdate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ChatBridgeService {

    private final TelegramClient telegram;
    private final TelegramTopicStore topicStore;
    private final SessionRegistry sessions;

    public ChatBridgeService(TelegramClient telegram,
                             TelegramTopicStore topicStore,
                             SessionRegistry sessions) {
        this.telegram = telegram;
        this.topicStore = topicStore;
        this.sessions = sessions;
    }

    public Mono<Void> fromSite(ChatSendRequest req) {
        var ts = topicStore.getOrCreate(req.cid(), req.clientName());

        Mono<Long> ensureThreadMono = (ts.threadId() != null)
                ? Mono.just(ts.threadId())
                : telegram.createForumTopic(buildTopicTitle(ts))
                .doOnNext(threadId -> topicStore.bindThread(req.cid(), threadId));

        return ensureThreadMono
                .flatMap(threadId -> telegram.sendMessage(buildAdminText(req, ts.shortCode()), threadId));
    }

    public Mono<Void> fromTelegram(TelegramUpdate upd) {
        if (upd == null || upd.message() == null) return Mono.empty();

        var msg = upd.message();

        String cid = topicStore.findCidByThreadId(msg.messageThreadId());
        if (cid == null) return Mono.empty();

        String text = msg.text();
        if (text == null || text.isBlank()) return Mono.empty();

        sessions.emit(cid, WsOutboundMessage.msg(text.trim()));
        return Mono.empty();
    }

    private String buildTopicTitle(TelegramTopicStore.TopicSession ts) {
        String name = safeName(ts.clientName());
        return (name == null ? "Клієнт" : name) + " #" + ts.shortCode();
    }

    private String safeName(String s) {
        if (s == null) return null;
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.isBlank()) return null;
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private String buildAdminText(ChatSendRequest req, String shortCode) {
        String client = safeName(req.clientName());

        StringBuilder sb = new StringBuilder();
        sb.append("🟦 Повідомлення з сайту\n");
        sb.append("Клієнт: ").append(client == null ? "Невідомо" : client)
                .append(" #").append(shortCode).append("\n\n");
        sb.append(req.text());

        return sb.toString();
    }
}