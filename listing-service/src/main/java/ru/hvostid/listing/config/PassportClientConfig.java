package ru.hvostid.listing.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PassportClientConfig {
    @Bean
    public RestClient passportRestClient(
            RestClient.Builder builder, @Value("${hvostid.services.passport-url:http://localhost:8083}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return builder.clone().baseUrl(url).requestFactory(factory).build();
    }
}
