package com.anvritai.abhay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "abhay.security")
public record SecurityProperties(String jwtSecret, long jwtExpirationMinutes, String corsOrigins) {
}
