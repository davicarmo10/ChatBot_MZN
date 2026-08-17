package br.com.bemnascer.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "evolution")
public record EvolutionProperties(
        String baseUrl,
        String apiKey,
        String instance,
        boolean sendEnabled
) {}
