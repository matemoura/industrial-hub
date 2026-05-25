package com.industrialhub.backend.common.webhook.presentation;

import com.industrialhub.backend.common.webhook.application.dto.CreateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.UpdateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.WebhookDeliveryResponse;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.application.dto.WebhookTestResponse;
import com.industrialhub.backend.common.webhook.application.usecase.ActivateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.CreateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.DeleteWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.GetWebhookDeliveriesUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.ListWebhookSubscriptionsUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.TestWebhookUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.UpdateWebhookSubscriptionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class WebhookController {

    private final CreateWebhookSubscriptionUseCase createUseCase;
    private final UpdateWebhookSubscriptionUseCase updateUseCase;
    private final DeleteWebhookSubscriptionUseCase deleteUseCase;
    private final ListWebhookSubscriptionsUseCase listUseCase;
    private final GetWebhookDeliveriesUseCase deliveriesUseCase;
    private final TestWebhookUseCase testUseCase;
    private final ActivateWebhookSubscriptionUseCase activateUseCase;

    public WebhookController(CreateWebhookSubscriptionUseCase createUseCase,
                              UpdateWebhookSubscriptionUseCase updateUseCase,
                              DeleteWebhookSubscriptionUseCase deleteUseCase,
                              ListWebhookSubscriptionsUseCase listUseCase,
                              GetWebhookDeliveriesUseCase deliveriesUseCase,
                              TestWebhookUseCase testUseCase,
                              ActivateWebhookSubscriptionUseCase activateUseCase) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.listUseCase = listUseCase;
        this.deliveriesUseCase = deliveriesUseCase;
        this.testUseCase = testUseCase;
        this.activateUseCase = activateUseCase;
    }

    @GetMapping
    public List<WebhookSubscriptionResponse> listAll() {
        return listUseCase.execute();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookSubscriptionResponse create(@Valid @RequestBody CreateWebhookRequest request,
                                               @AuthenticationPrincipal UserDetails user) {
        return createUseCase.execute(request, user.getUsername());
    }

    @PutMapping("/{id}")
    public WebhookSubscriptionResponse update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateWebhookRequest request,
                                               @AuthenticationPrincipal UserDetails user) {
        return updateUseCase.execute(id, request, user.getUsername());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @AuthenticationPrincipal UserDetails user) {
        deleteUseCase.execute(id, user.getUsername());
    }

    @PostMapping("/{id}/test")
    public WebhookTestResponse test(@PathVariable UUID id,
                                    @AuthenticationPrincipal UserDetails user) {
        return testUseCase.execute(id, user.getUsername());
    }

    @GetMapping("/{id}/deliveries")
    public List<WebhookDeliveryResponse> getDeliveries(@PathVariable UUID id) {
        return deliveriesUseCase.execute(id);
    }

    @PutMapping("/{id}/activate")
    public WebhookSubscriptionResponse activate(@PathVariable UUID id,
                                                 @AuthenticationPrincipal UserDetails user) {
        return activateUseCase.execute(id, user.getUsername());
    }
}
