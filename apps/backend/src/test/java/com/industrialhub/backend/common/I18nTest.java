package com.industrialhub.backend.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTest {

    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        messageSource = source;
    }

    @Test
    void ptBR_invalidCredentials() {
        String msg = messageSource.getMessage("error.invalid.credentials", null, new Locale("pt", "BR"));
        assertThat(msg).isEqualTo("Credenciais inválidas");
    }

    @Test
    void enUS_invalidCredentials() {
        String msg = messageSource.getMessage("error.invalid.credentials", null, Locale.US);
        assertThat(msg).isEqualTo("Invalid credentials");
    }

    @Test
    void esES_userNotFound() {
        String msg = messageSource.getMessage("error.user.not.found", new Object[]{"joao"}, new Locale("es", "ES"));
        assertThat(msg).isEqualTo("Usuario no encontrado: joao");
    }

    @Test
    void frFR_invalidRetentionDays() {
        String msg = messageSource.getMessage("error.invalid.retention.days", new Object[]{10}, new Locale("fr", "FR"));
        assertThat(msg).contains("10");
    }
}
