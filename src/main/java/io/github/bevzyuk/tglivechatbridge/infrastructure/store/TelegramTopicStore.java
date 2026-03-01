package io.github.bevzyuk.tglivechatbridge.infrastructure.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Component
public class TelegramTopicStore {

    public static final class TopicSession {
        private final String cid;
        private final String shortCode;
        private volatile String clientName;
        private volatile Long threadId;

        public TopicSession(String cid, String shortCode, String clientName) {
            this.cid = cid;
            this.shortCode = shortCode;
            this.clientName = clientName;
        }

        public String cid() { return cid; }
        public String shortCode() { return shortCode; }
        public String clientName() { return clientName; }
        public Long threadId() { return threadId; }

        public void setClientName(String clientName) {
            if (clientName != null && !clientName.isBlank()) {
                this.clientName = clientName.trim();
            }
        }

        public void setThreadId(Long threadId) {
            this.threadId = threadId;
        }
    }

    private final Cache<String, TopicSession> byCid = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(1_000)
            .scheduler(com.github.benmanes.caffeine.cache.Scheduler.systemScheduler())
            .build();

    private final Cache<Long, String> cidByThread = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(1_000)
            .scheduler(com.github.benmanes.caffeine.cache.Scheduler.systemScheduler())
            .build();

    public TopicSession getOrCreate(String cid, String clientName) {
        TopicSession s = byCid.get(cid, key ->
                new TopicSession(key, newShortCode(), normalizeName(clientName)));

        Long tid = s.threadId();
        if (tid != null) {
            cidByThread.put(tid, cid);
        }

        return s;
    }

    public void bindThread(String cid, long threadId) {
        TopicSession s = byCid.getIfPresent(cid);
        if (s != null) {
            s.setThreadId(threadId);
        }
        cidByThread.put(threadId, cid);
    }

    public String findCidByThreadId(Long threadId) {
        if (threadId == null) return null;
        String cid = cidByThread.getIfPresent(threadId);
        if (cid != null) {
            byCid.getIfPresent(cid);
        }
        return cid;
    }

    private static String normalizeName(String s) {
        if (s == null) return null;
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.isBlank()) return null;
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    private static String newShortCode() {
        return UUID.randomUUID().toString().replace("-", "")
                .substring(0, 4).toUpperCase(Locale.ROOT);
    }
}
