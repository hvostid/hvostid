package ru.hvostid.common.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import ru.hvostid.common.exception.GlobalErrorHandler;
import ru.hvostid.common.web.RequestIdMdcFilter;

/**
 * Auto-registers the shared RFC 7807 error handler and the {@code X-Request-Id} -> MDC
 * filter for every Spring Boot service that depends on {@code common}. Listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(
        name = {
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.security.access.AccessDeniedException"
        })
public class HvostidErrorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RequestIdMdcFilter requestIdMdcFilter() {
        return new RequestIdMdcFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalErrorHandler hvostidGlobalErrorHandler() {
        return new GlobalErrorHandler();
    }
}
