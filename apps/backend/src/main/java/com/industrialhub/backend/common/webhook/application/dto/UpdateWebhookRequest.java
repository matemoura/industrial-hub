package com.industrialhub.backend.common.webhook.application.dto;

import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateWebhookRequest(

    @Size(max = 500)
    @Pattern(regexp = "https://.*|http://localhost.*|http://127\\.0\\.0\\.1.*",
             message = "URL deve usar HTTPS")
    String url,

    @Size(max = 100)
    String secret,

    @NotEmpty(message = "Pelo menos um evento deve ser selecionado")
    Set<WebhookEvent> events,

    @Size(max = 255)
    String description
) {}
