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
    public RestClient listingRestClient(HvostidServiceProperties properties, RestClient.Builder builder) {
        return buildRestClient(properties.listingService(), builder);
    }

    @Bean
    public RestClient passportRestClient(HvostidServiceProperties properties, RestClient.Builder builder) {
        return buildRestClient(properties.passportService(), builder);
    }

    private static RestClient buildRestClient(
            HvostidServiceProperties.ServiceEndpoint endpoint, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(endpoint.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(endpoint.readTimeout());

        return builder.clone()
                .baseUrl(endpoint.url())
                .requestFactory(requestFactory)
                .build();
    }
}
