package io.github.bevzyuk.tglivechatbridge.web;

import io.github.bevzyuk.tglivechatbridge.application.dto.ChatSendRequest;
import io.github.bevzyuk.tglivechatbridge.application.service.ChatBridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatBridgeService bridge;

    public ChatController(ChatBridgeService bridge) {
        this.bridge = bridge;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> send(@Valid @RequestBody ChatSendRequest req) {
        return bridge.fromSite(req);
    }
}
