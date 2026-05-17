package ru.hvostid.matching.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(endpoint.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(endpoint.readTimeout());

        return RestClient.builder()
                .baseUrl(endpoint.url())
                .requestFactory(requestFactory)
                .build();
    }
}
