package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.webhook.application.dto.WebhookDeliveryResponse;
import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryResponseTest {

    private WebhookDelivery buildDelivery(Integer responseCode) {
        return WebhookDelivery.builder()
                .id(UUID.randomUUID())
                .event(WebhookEvent.NC_CREATED)
                .attempt(1)
                .responseCode(responseCode)
                .durationMs(42L)
                .status(responseCode != null && responseCode >= 200 && responseCode < 300
                        ? DeliveryStatus.SUCCESS
                        : DeliveryStatus.FAILED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void from_responseCode200_successIsTrue() {
        WebhookDelivery delivery = buildDelivery(200);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isTrue();
    }

    @Test
    void from_responseCode201_successIsTrue() {
        WebhookDelivery delivery = buildDelivery(201);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isTrue();
    }

    @Test
    void from_responseCode299_successIsTrue() {
        WebhookDelivery delivery = buildDelivery(299);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isTrue();
    }

    @Test
    void from_responseCode500_successIsFalse() {
        WebhookDelivery delivery = buildDelivery(500);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isFalse();
    }

    @Test
    void from_responseCode400_successIsFalse() {
        WebhookDelivery delivery = buildDelivery(400);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isFalse();
    }

    @Test
    void from_responseCodeNull_successIsFalse() {
        WebhookDelivery delivery = buildDelivery(null);
        WebhookDeliveryResponse response = WebhookDeliveryResponse.from(delivery);
        assertThat(response.success()).isFalse();
    }
}
