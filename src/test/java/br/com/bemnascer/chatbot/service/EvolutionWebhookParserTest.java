package br.com.bemnascer.chatbot.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionWebhookParserTest {
    private final EvolutionWebhookParser parser = new EvolutionWebhookParser();

    static Map<String, Object> payload(String jid, boolean fromMe, String id) {
        return Map.of("event", "messages.upsert", "instance", "MZN-LOCAL", "data", Map.of(
                "key", Map.of("remoteJid", jid, "fromMe", fromMe, "id", id),
                "message", Map.of("conversation", "bom dia")));
    }

    @Test
    void acceptsExactUpsertEventAndPreservesMessageIdentity() {
        var payload = payload(BotTestSupport.NUMBER + "@s.whatsapp.net", false, "message-1");
        assertThat(parser.isMessagesUpsert(payload)).isTrue();
        var incoming = parser.extractIncomingMessage(payload).orElseThrow();
        assertThat(incoming.messageId()).isEqualTo("message-1");
        assertThat(incoming.instance()).isEqualTo("MZN-LOCAL");
        assertThat(incoming.number()).isEqualTo(BotTestSupport.NUMBER);
        assertThat(parser.isMessagesUpsert(Map.of("event", "MESSAGES_UPSERT"))).isTrue();
        assertThat(parser.isMessagesUpsert(Map.of("event", "not.messages.upsert"))).isFalse();
    }

    @Test
    void ignoresSelfGroupStatusAndEventsWithoutId() {
        assertThat(parser.extractIncomingMessage(payload(BotTestSupport.NUMBER + "@s.whatsapp.net", true, "1"))).isEmpty();
        assertThat(parser.extractIncomingMessage(payload("123456789@g.us", false, "1"))).isEmpty();
        assertThat(parser.extractIncomingMessage(payload("status@broadcast", false, "1"))).isEmpty();
        assertThat(parser.extractIncomingMessage(payload(BotTestSupport.NUMBER + "@s.whatsapp.net", false, ""))).isEmpty();
    }

    @Test
    void lidRequiresExplicitPhoneJidAndIsNeverTreatedAsPhoneNumber() {
        var unresolved = payload("123456789@lid", false, "1");
        assertThat(parser.extractIncomingMessage(unresolved)).isEmpty();
        var resolved = new HashMap<String, Object>(unresolved);
        resolved.put("data", Map.of("key", Map.of("remoteJid", "123456789@lid", "remoteJidAlt",
                BotTestSupport.NUMBER + "@s.whatsapp.net", "fromMe", false, "id", "1"),
                "message", Map.of("extendedTextMessage", Map.of("text", "quais documentos?"))));
        assertThat(parser.extractIncomingMessage(resolved).orElseThrow().number()).isEqualTo(BotTestSupport.NUMBER);
    }
}
