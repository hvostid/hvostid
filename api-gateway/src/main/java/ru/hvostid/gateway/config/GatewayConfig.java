package ru.hvostid.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the API Gateway.
 * <p>
 * Creates a RestClient bean for calling Auth Service introspection endpoint
 * and enables typed configuration properties.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, RateLimitProperties.class, CorsProperties.class})
public class GatewayConfig {
    @Bean
    public RestClient introspectionRestClient(AuthProperties authProperties) {
        int timeoutMillis = (int) authProperties.introspectTimeout().toMillis();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);

        return RestClient.builder()
                .baseUrl(authProperties.introspectUrl())
                .requestFactory(factory)
                .build();
    }
}
