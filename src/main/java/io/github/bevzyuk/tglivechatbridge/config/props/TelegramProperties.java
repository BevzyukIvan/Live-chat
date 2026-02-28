package io.github.bevzyuk.tglivechatbridge.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        long adminChatId,
        String webhookSecret,
        Duration linkTtl
) { }
