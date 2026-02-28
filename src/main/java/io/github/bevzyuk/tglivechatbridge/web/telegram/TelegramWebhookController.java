package io.github.bevzyuk.tglivechatbridge.web.telegram;

import io.github.bevzyuk.tglivechatbridge.application.service.ChatBridgeService;
import io.github.bevzyuk.tglivechatbridge.config.props.TelegramProperties;
import io.github.bevzyuk.tglivechatbridge.web.telegram.dto.TelegramUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final ChatBridgeService bridge;
    private final TelegramProperties props;

    public TelegramWebhookController(ChatBridgeService bridge, TelegramProperties props) {
        this.bridge = bridge;
        this.props = props;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> onUpdate(
            @RequestBody TelegramUpdate update,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret
    ) {
        String expected = props.webhookSecret();
        if (expected != null && !expected.isBlank()) {
            if (secret == null || !expected.equals(secret)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad secret token"));
            }
        }
        return bridge.fromTelegram(update);
    }
}
