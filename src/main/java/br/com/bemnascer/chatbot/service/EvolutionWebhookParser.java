package br.com.bemnascer.chatbot.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class EvolutionWebhookParser {

    public boolean isMessagesUpsert(Map<String, Object> payload) {
        String event = asString(payload.get("event"));
        if (event == null) {
            return false;
        }
        String normalized = event.toLowerCase().replace('_', '.');
        return normalized.contains("messages.upsert");
    }

    public Optional<IncomingMessage> extractIncomingMessage(Map<String, Object> payload) {
        Map<String, Object> data = asMap(payload.get("data"));
        if (data == null) {
            return Optional.empty();
        }

        Map<String, Object> key = asMap(data.get("key"));
        if (key == null) {
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(key.get("fromMe"))) {
            return Optional.empty();
        }

        String remoteJid = asString(key.get("remoteJid"));
        if (remoteJid == null || remoteJid.endsWith("@g.us")) {
            return Optional.empty();
        }

        Map<String, Object> message = asMap(data.get("message"));
        if (message == null) {
            return Optional.empty();
        }

        String text = firstNonBlank(
                asString(message.get("conversation")),
                nestedString(message, "extendedTextMessage", "text"),
                nestedString(message, "imageMessage", "caption"),
                nestedString(message, "videoMessage", "caption")
        );

        if (text == null) {
            return Optional.empty();
        }

        String number = remoteJid.contains("@") ? remoteJid.substring(0, remoteJid.indexOf('@')) : remoteJid;
        String pushName = asString(data.get("pushName"));
        return Optional.of(new IncomingMessage(number, pushName, text));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String nestedString(Map<String, Object> root, String objectKey, String valueKey) {
        Map<String, Object> nested = asMap(root.get(objectKey));
        return nested == null ? null : asString(nested.get(valueKey));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record IncomingMessage(String number, String pushName, String text) {}
}
