package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BotProperties;
import br.com.bemnascer.chatbot.config.EvolutionProperties;
import br.com.bemnascer.chatbot.service.EvolutionWebhookParser.IncomingMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Repository
public class WebhookQueue {
    private static final Logger log = LoggerFactory.getLogger(WebhookQueue.class);
    public enum Admission { ACCEPTED, DUPLICATE, IGNORED, FULL }
    public record Event(String key, String conversation, String instance, String number, String text,
                        boolean oversized, boolean sendRequested, Instant receivedAt) {}

    private final JdbcTemplate jdbc;
    private final BotProperties properties;
    private final EvolutionProperties evolution;
    private final Clock clock;

    public WebhookQueue(JdbcTemplate jdbc, BotProperties properties, EvolutionProperties evolution, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.evolution = evolution;
        this.clock = clock;
    }

    public boolean allowed(String instance, String number) {
        return instance.equals(evolution.instance()) && properties.allows(number);
    }

    @Transactional
    public synchronized Admission enqueue(IncomingMessage message, boolean sendRequested) {
        if (!message.instance().equals(evolution.instance())) {
            log.info("Mensagem ignorada; motivo=INSTANCE_MISMATCH; instancia_recebida={}; EVOLUTION_INSTANCE={}",
                    label(message.instance()), label(evolution.instance()));
            return Admission.IGNORED;
        }
        if (!properties.allows(message.number())) {
            String reason = properties.allowedNumbers().isEmpty() ? "EMPTY_ALLOWLIST" : "NUMBER_NOT_ALLOWED";
            String number = message.number();
            log.info("Mensagem ignorada; motivo={}; remetente=***{}; digitos={}; confira BOT_ALLOWED_NUMBERS",
                    reason, number.substring(Math.max(0, number.length() - 4)), number.length());
            return Admission.IGNORED;
        }
        String key = eventKey(message.instance(), message.messageId());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM bot_event WHERE event_key = ?", Long.class, key) > 0) {
            return Admission.DUPLICATE;
        }
        long pending = jdbc.queryForObject("SELECT COUNT(*) FROM bot_event WHERE event_status IN ('PENDING','PROCESSING','SENDING')", Long.class);
        long stored = jdbc.queryForObject("SELECT COUNT(*) FROM bot_event", Long.class);
        // Also bound the seven-day deduplication ledger. Never silently forget recent IDs to make room.
        if (pending >= properties.maxPendingEvents() || stored >= 20000) return Admission.FULL;
        boolean oversized = message.text().length() > properties.maxTextCharacters();
        try {
            jdbc.update("""
                    INSERT INTO bot_event (event_key, conversation_key, instance_name, phone_number,
                    content, oversized, send_requested, received_at, event_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """, key, "wa:" + message.instance() + ":" + message.number(), message.instance(), message.number(),
                    oversized ? "" : message.text(), oversized, sendRequested, clock.millis());
        } catch (DuplicateKeyException duplicate) {
            return Admission.DUPLICATE;
        }
        return Admission.ACCEPTED;
    }

    @Transactional
    public Optional<Event> claimNext() {
        var events = jdbc.query("""
                SELECT * FROM bot_event WHERE event_status = 'PENDING' ORDER BY sequence_id LIMIT 1
                """, (rs, row) -> new Event(rs.getString("event_key"), rs.getString("conversation_key"),
                rs.getString("instance_name"), rs.getString("phone_number"), rs.getString("content"),
                rs.getBoolean("oversized"), rs.getBoolean("send_requested"), Instant.ofEpochMilli(rs.getLong("received_at"))));
        if (events.isEmpty()) return Optional.empty();
        Event event = events.getFirst();
        int claimed = jdbc.update("UPDATE bot_event SET event_status = 'PROCESSING' WHERE event_key = ? AND event_status = 'PENDING'", event.key());
        return claimed == 1 ? Optional.of(event) : Optional.empty();
    }

    public void markSending(String key) {
        jdbc.update("UPDATE bot_event SET event_status = 'SENDING' WHERE event_key = ?", key);
    }

    public void finish(String key, String status) {
        jdbc.update("UPDATE bot_event SET event_status = ?, content = '' WHERE event_key = ?", status, key);
    }

    public void recover() {
        // Delivery could already have succeeded before a crash: never resend an ambiguous send.
        jdbc.update("UPDATE bot_event SET event_status = 'UNCERTAIN', content = '' WHERE event_status = 'SENDING'");
        jdbc.update("UPDATE bot_event SET event_status = 'PENDING' WHERE event_status = 'PROCESSING'");
    }

    private String eventKey(String instance, String id) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((instance + "\0" + id).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 não disponível", impossible);
        }
    }

    private String label(String value) {
        return value == null ? "(vazia)" : value.substring(0, Math.min(128, value.length()))
                .replaceAll("\\p{Cntrl}", "?");
    }
}
