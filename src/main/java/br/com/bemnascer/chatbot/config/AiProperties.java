package br.com.bemnascer.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("qwen3:4b") String model,
        @DefaultValue("60s") Duration timeout) {}
