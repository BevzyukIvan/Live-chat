package io.github.bevzyuk.tglivechatbridge.infrastructure.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.bevzyuk.tglivechatbridge.config.props.TelegramProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TelegramClient {

    private final TelegramProperties props;
    private final WebClient client;

    public TelegramClient(TelegramProperties props) {
        this.props = props;
        String baseUrl = "https://api.telegram.org/bot" + props.botToken();
        this.client = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<Void> sendMessage(String text, long messageThreadId) {
        SendMessageRequest req = new SendMessageRequest(
                props.adminChatId(),
                text,
                true,
                messageThreadId
        );

        return client.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(SendMessageResponse.class)
                .flatMap(resp -> {
                    if (resp == null || !resp.ok) {
                        String desc = (resp == null ? "null response" : resp.description);
                        Integer code = (resp == null ? null : resp.error_code);
                        String msg = "Telegram sendMessage failed"
                                + (code != null ? " (" + code + ")" : "")
                                + (desc != null ? ": " + desc : "");
                        return Mono.error(new IllegalStateException(msg));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Long> createForumTopic(String topicName) {
        CreateForumTopicRequest req = new CreateForumTopicRequest(props.adminChatId(), topicName);

        return client.post()
                .uri("/createForumTopic")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(CreateForumTopicResponse.class)
                .map(resp -> {
                    if (resp == null || !resp.ok || resp.result == null || resp.result.message_thread_id == null) {
                        String desc = (resp == null ? "null response" : resp.description);
                        Integer code = (resp == null ? null : resp.error_code);
                        String msg = "Telegram createForumTopic failed"
                                + (code != null ? " (" + code + ")" : "")
                                + (desc != null ? ": " + desc : "");
                        throw new IllegalStateException(msg);
                    }
                    return resp.result.message_thread_id;
                });
    }

    public record SendMessageRequest(
            long chat_id,
            String text,
            boolean disable_web_page_preview,
            long message_thread_id
    ) { }

    public record CreateForumTopicRequest(long chat_id, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SendMessageResponse {
        public boolean ok;
        public Integer error_code;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class CreateForumTopicResponse {
        public boolean ok;
        public Integer error_code;
        public String description;
        public Result result;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class Result {
            public Long message_thread_id;
        }
    }
}