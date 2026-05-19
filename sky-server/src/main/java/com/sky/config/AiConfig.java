package com.sky.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sky.ai")
@Data
public class AiConfig {
    private String apiKey;
    private String model;
    private Integer maxTokens;
    private Double temperature;
}
