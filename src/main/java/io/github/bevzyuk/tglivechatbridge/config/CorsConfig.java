package io.github.bevzyuk.tglivechatbridge.config;

import io.github.bevzyuk.tglivechatbridge.config.props.AppCorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    private final AppCorsProperties corsProps;

    public CorsConfig(AppCorsProperties corsProps) {
        this.corsProps = corsProps;
    }

    @Bean
    CorsWebFilter corsWebFilter() {
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = corsProps.allowedOrigins();
        if (origins != null && origins.contains("*")) {
            cfg.addAllowedOriginPattern("*");
        } else if (origins != null) {
            origins.forEach(cfg::addAllowedOrigin);
        }

        cfg.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.OPTIONS.name()
        ));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setMaxAge(3600L);
        cfg.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsWebFilter(source);
    }
}
