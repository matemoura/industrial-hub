package com.industrialhub.backend.common.webhook.service;

import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

@Component
public class WebhookUrlValidator {

    public void validate(String urlString) {
        String host;
        try {
            host = new URL(urlString).getHost();
        } catch (MalformedURLException e) {
            throw new WebhookInvalidUrlException("URL inválida: " + urlString);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
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
