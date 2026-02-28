package io.github.bevzyuk.tglivechatbridge.config;

import io.github.bevzyuk.tglivechatbridge.config.props.AppCorsProperties;
import io.github.bevzyuk.tglivechatbridge.config.props.TelegramProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppCorsProperties.class, TelegramProperties.class})
public class ConfigPropsConfig { }
