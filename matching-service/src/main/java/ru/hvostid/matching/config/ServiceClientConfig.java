package ru.hvostid.matching.config;

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
        return buildRestClient(properties.listingService());
    }

    @Bean
    public RestClient passportRestClient(HvostidServiceProperties properties) {
        return buildRestClient(properties.passportService());
    }

    private static RestClient buildRestClient(HvostidServiceProperties.ServiceEndpoint endpoint) {
        int connectMillis = (int) endpoint.connectTimeout().toMillis();
        int readMillis = (int) endpoint.readTimeout().toMillis();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMillis);
        factory.setReadTimeout(readMillis);

        return RestClient.builder()
                .baseUrl(endpoint.url())
                .requestFactory(factory)
                .build();
    }
}
