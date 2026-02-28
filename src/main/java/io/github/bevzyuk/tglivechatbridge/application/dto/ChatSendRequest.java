package io.github.bevzyuk.tglivechatbridge.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatSendRequest(
        @NotBlank @Size(max = 64) String cid,
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 64) String clientName,
        @Size(max = 2048) String pageUrl,
        @Size(max = 2048) String referrer
) { }