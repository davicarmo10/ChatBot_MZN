package br.com.bemnascer.chatbot.service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@Component
public class EvolutionWebhookParser {
    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookParser.class);

    public boolean isMessagesUpsert(Map<String, Object> payload) {
        String event = asString(payload.get("event"));
        if (event == null) {
            return false;
        }
        String normalized = event.toLowerCase(Locale.ROOT).replace('_', '.');
        return normalized.equals("messages.upsert");
    }

    public Optional<IncomingMessage> extractIncomingMessage(Map<String, Object> payload) {
        Map<String, Object> data = asMap(payload.get("data"));
        if (data == null) {
            return ignored("DATA_INVALID");
        }

        Map<String, Object> key = asMap(data.get("key"));
        if (key == null) {
            return ignored("KEY_MISSING");
        }

        if (Boolean.TRUE.equals(key.get("fromMe"))) return ignored("FROM_ME");
        if (!Boolean.FALSE.equals(key.get("fromMe"))) {
            return ignored("FROM_ME_INVALID");
        }

        String remoteJid = asString(key.get("remoteJid"));
        if (remoteJid == null || remoteJid.endsWith("@g.us") || remoteJid.endsWith("@broadcast")) {
            return ignored("NON_PRIVATE_JID");
        }
        // Modern WhatsApp may deliver a LID plus the real phone JID in remoteJidAlt.
        if (remoteJid.endsWith("@lid")) {
            remoteJid = asString(key.get("remoteJidAlt"));
            if (remoteJid == null || remoteJid.isBlank()) return ignored("LID_WITHOUT_PHONE");
        }
        if (!remoteJid.matches("[0-9]{8,15}@s\\.whatsapp\\.net")) return ignored("PHONE_JID_INVALID");
        String instance = asString(payload.get("instance"));
        String messageId = asString(key.get("id"));
        if (instance == null || instance.isBlank() || instance.length() > 128
                || messageId == null || messageId.isBlank() || messageId.length() > 256) {
            return ignored("INSTANCE_OR_MESSAGE_ID_INVALID");
        }

        Map<String, Object> message = asMap(data.get("message"));
        if (message == null) {
            return ignored("MESSAGE_MISSING");
        }

        String text = firstNonBlank(
                asString(message.get("conversation")),
                nestedString(message, "extendedTextMessage", "text"),
                nestedString(message, "imageMessage", "caption"),
                nestedString(message, "videoMessage", "caption")
        );

        if (text == null) {
            return ignored("NO_TEXT");
        }

        String number = remoteJid.contains("@") ? remoteJid.substring(0, remoteJid.indexOf('@')) : remoteJid;
        return Optional.of(new IncomingMessage(instance, messageId, number, text));
    }

    private Optional<IncomingMessage> ignored(String reason) {
        log.info("Mensagem do webhook ignorada; motivo={}", reason);
        return Optional.empty();
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

    public record IncomingMessage(String instance, String messageId, String number, String text) {}
}
