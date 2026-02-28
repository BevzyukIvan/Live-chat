package io.github.bevzyuk.tglivechatbridge;

import io.github.bevzyuk.tglivechatbridge.config.props.AppCorsProperties;
import io.github.bevzyuk.tglivechatbridge.config.props.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class TgLivechatBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgLivechatBridgeApplication.class, args);
    }

}
