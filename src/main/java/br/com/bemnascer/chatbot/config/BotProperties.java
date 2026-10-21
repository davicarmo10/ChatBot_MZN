package br.com.bemnascer.chatbot.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        List<String> allowedNumbers,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("24h") Duration greetingIdle,
        @DefaultValue("200") @Min(2) int maxContactMessages,
        @DefaultValue("2000") @Min(2) int maxTotalMessages,
        @DefaultValue("12") @Min(2) int contextMessages,
        @DefaultValue("8000") @Min(2000) int contextCharacters,
        @DefaultValue("2000") @Min(100) @Max(2000) int maxTextCharacters,
        @DefaultValue("2000") @Min(1) int maxPendingEvents) {

    public BotProperties {
        allowedNumbers = allowedNumbers == null ? List.of() : List.copyOf(allowedNumbers);
        if (retention != null && (retention.isNegative() || retention.isZero())) {
            throw new IllegalArgumentException("bot.retention deve ser positiva");
        }
        if (greetingIdle != null && (greetingIdle.isNegative() || greetingIdle.isZero())) {
            throw new IllegalArgumentException("bot.greeting-idle deve ser positivo");
        }
    }

    public boolean allows(String number) {
        return number != null && number.matches("[0-9]{8,15}") && allowedNumbers.stream()
                .map(value -> value.replaceAll("[^0-9]", ""))
                .anyMatch(number::equals);
    }
}
