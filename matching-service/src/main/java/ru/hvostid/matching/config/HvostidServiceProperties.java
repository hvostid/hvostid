package ru.hvostid.matching.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hvostid")
public record HvostidServiceProperties(ServiceEndpoint listingService, ServiceEndpoint passportService) {

    public record ServiceEndpoint(String url, Duration connectTimeout, Duration readTimeout) {
        public ServiceEndpoint {
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(2);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(3);
            }
        }
    }
}
