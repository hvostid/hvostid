package ru.hvostid.passport.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the media module configuration. StringRedisTemplate is provided by
 * spring-boot-starter-data-redis autoconfiguration.
 */
@Configuration
@EnableConfigurationProperties(MediaTicketProperties.class)
public class MediaConfig {}
