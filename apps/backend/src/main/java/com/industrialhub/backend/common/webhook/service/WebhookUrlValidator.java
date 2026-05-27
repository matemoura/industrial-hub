package com.industrialhub.backend.common.webhook.service;

import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.*;

/**
 * Validates webhook URLs by resolving the hostname with a 2-second DNS timeout
 * and blocking private/reserved IP ranges (SEC-092, SEC-106).
 */
@Component
public class WebhookUrlValidator {

    // Shared executor — not created per call to avoid thread pool exhaustion
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "webhook-dns-resolver");
        t.setDaemon(true);
        return t;
    });

    public void validate(String urlString) {
        String host;
        try {
            host = new URL(urlString).getHost();
        } catch (MalformedURLException e) {
            throw new WebhookInvalidUrlException("URL inválida: " + urlString);
        }

        InetAddress address;
        try {
            // SEC-106: resolve with explicit 2-second timeout to avoid blocking Tomcat thread
            Future<InetAddress> future = executorService.submit(() -> InetAddress.getByName(host));
            address = future.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new WebhookInvalidUrlException("Timeout na resolução DNS do hostname: " + host);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebhookInvalidUrlException("Resolução DNS interrompida para o hostname: " + host);
        } catch (ExecutionException e) {
            throw new WebhookInvalidUrlException("Hostname não pode ser resolvido: " + host);
        }

        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            throw new WebhookInvalidUrlException("URL aponta para endereço de rede privada ou reservada: " + host);
        }

        // AWS metadata endpoint — 169.254.169.254
        byte[] raw = address.getAddress();
        if (raw.length == 4 && (raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254) {
            throw new WebhookInvalidUrlException("URL aponta para endereço link-local (AWS metadata): " + host);
        }
    }
}
