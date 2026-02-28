package io.github.bevzyuk.tglivechatbridge.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record AppCorsProperties(List<String> allowedOrigins) { }
