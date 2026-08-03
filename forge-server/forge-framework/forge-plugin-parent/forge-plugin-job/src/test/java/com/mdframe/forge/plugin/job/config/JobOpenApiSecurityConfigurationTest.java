package com.mdframe.forge.plugin.job.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobOpenApiSecurityConfigurationTest {

    private final JobOpenApiSecurityConfiguration configuration = new JobOpenApiSecurityConfiguration();

    @Test
    void shouldDisableOpenApiByDefault() {
        assertFalse(new JobProperties().getOpenApi().getEnabled());
    }

    @Test
    void shouldFailStartupWhenEnabledWithoutPepper() {
        JobProperties properties = new JobProperties();
        properties.getOpenApi().setEnabled(true);

        assertThrows(IllegalStateException.class, () -> configuration.jobApiTokenCodec(properties));
    }

    @Test
    void shouldAllowDisabledOpenApiWithoutPepper() {
        JobProperties properties = new JobProperties();

        assertDoesNotThrow(() -> configuration.jobApiTokenCodec(properties));
    }
}
