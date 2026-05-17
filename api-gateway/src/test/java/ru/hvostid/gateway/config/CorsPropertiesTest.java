package ru.hvostid.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void parseOrigins_splitsCommaSeparatedList() {
        assertThat(CorsProperties.parseOrigins("http://localhost, https://app.example.com"))
                .containsExactly("http://localhost", "https://app.example.com");
    }

    @Test
    void parseOrigins_defaultsWhenBlank() {
        assertThat(CorsProperties.parseOrigins("  ")).containsExactly("http://localhost");
    }

    @Test
    void allowedOriginList_usesConfiguredValue() {
        assertThat(new CorsProperties("http://localhost:3000").allowedOriginList())
                .containsExactly("http://localhost:3000");
    }
}
