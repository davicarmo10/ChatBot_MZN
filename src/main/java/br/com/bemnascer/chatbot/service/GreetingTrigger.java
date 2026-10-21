package br.com.bemnascer.chatbot.service;

import java.util.Optional;
import java.util.regex.Pattern;

public final class GreetingTrigger {
    private static final Pattern PREFIX = Pattern.compile("^(bom dia|boa tarde|boa noite|oi|ola)(?:\\s+|$)");
    private GreetingTrigger() {}

    public record Greeting(String salutation, boolean standalone) {}

    public static Optional<Greeting> match(String normalizedText) {
        var matcher = PREFIX.matcher(normalizedText);
        if (!matcher.find()) return Optional.empty();
        String salutation = switch (matcher.group(1)) {
            case "bom dia" -> "Bom dia!";
            case "boa tarde" -> "Boa tarde!";
            case "boa noite" -> "Boa noite!";
            default -> "Olá!";
        };
        return Optional.of(new Greeting(salutation, normalizedText.substring(matcher.end()).isBlank()));
    }
}
