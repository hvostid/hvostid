package ru.hvostid.passport.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HvostidServiceProperties.class)
public class ServiceClientConfig {
    @Bean
    public RestClient listingRestClient(HvostidServiceProperties properties) {
        HvostidServiceProperties.ServiceEndpoint endpoint = properties.listingService();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) endpoint.connectTimeout().toMillis());
        factory.setReadTimeout((int) endpoint.readTimeout().toMillis());
        return RestClient.builder()
                .baseUrl(endpoint.url())
                .requestFactory(factory)
                .build();
    }
}
