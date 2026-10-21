package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BotProperties;
import br.com.bemnascer.chatbot.model.HistoryMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationStore {
    private final JdbcTemplate jdbc;
    private final BotProperties properties;
    private final Clock clock;

    public ConversationStore(JdbcTemplate jdbc, BotProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<Instant> lastIncoming(String conversation, String excludeSource) {
        Long time = jdbc.queryForObject("""
                SELECT MAX(created_at) FROM bot_history
                WHERE conversation_key = ? AND source_key <> ? AND message_role = 'user' AND created_at >= ?
                """, Long.class, conversation, excludeSource, cutoff());
        return Optional.ofNullable(time).map(Instant::ofEpochMilli);
    }

    public List<HistoryMessage> context(String conversation, String excludeSource, int currentCharacters) {
        List<HistoryMessage> recent = jdbc.query("""
                SELECT message_role, content, created_at FROM bot_history
                WHERE conversation_key = ? AND source_key <> ? AND created_at >= ?
                AND (message_role = 'user' OR NOT EXISTS
                    (SELECT 1 FROM bot_event e WHERE e.event_key = bot_history.source_key
                     AND e.event_status NOT IN ('SENT', 'SIMULATED')))
                ORDER BY id DESC LIMIT ?
                """, (rs, row) -> new HistoryMessage(rs.getString(1), rs.getString(2),
                Instant.ofEpochMilli(rs.getLong(3))), conversation, excludeSource, cutoff(), properties.contextMessages());
        List<HistoryMessage> selected = new ArrayList<>();
        int remaining = properties.contextCharacters() - currentCharacters;
        for (HistoryMessage message : recent) {
            if (message.content().length() > remaining) break;
            selected.add(message);
            remaining -= message.content().length();
        }
        Collections.reverse(selected);
        // Never start context with an orphaned assistant answer.
        if (!selected.isEmpty() && selected.getFirst().role().equals("assistant")) selected.removeFirst();
        return List.copyOf(selected);
    }

    @Transactional
    public void appendExchange(String conversation, String source, String incoming, String answer, Instant receivedAt) {
        append(conversation, source, "user", incoming, receivedAt);
        append(conversation, source, "assistant", answer, clock.instant());
        trimConversation(conversation);
        trimGlobal();
    }

    private void append(String conversation, String source, String role, String content, Instant at) {
        jdbc.update("""
                MERGE INTO bot_history (conversation_key, source_key, message_role, content, created_at)
                KEY(source_key, message_role) VALUES (?, ?, ?, ?, ?)
                """, conversation, source, role, content.substring(0, Math.min(2000, content.length())), at.toEpochMilli());
    }

    @Transactional
    public void cleanup() {
        jdbc.update("DELETE FROM bot_history WHERE created_at < ?", cutoff());
        for (String conversation : jdbc.queryForList("SELECT DISTINCT conversation_key FROM bot_history", String.class)) {
            trimConversation(conversation);
        }
        trimGlobal();
        jdbc.update("DELETE FROM bot_event WHERE received_at < ?", cutoff());
    }

    private void trimConversation(String conversation) {
        jdbc.update("""
                DELETE FROM bot_history WHERE conversation_key = ? AND id NOT IN
                (SELECT id FROM bot_history WHERE conversation_key = ? ORDER BY id DESC LIMIT ?)
                """, conversation, conversation, properties.maxContactMessages());
    }

    private void trimGlobal() {
        jdbc.update("""
                DELETE FROM bot_history WHERE id NOT IN
                (SELECT id FROM bot_history ORDER BY id DESC LIMIT ?)
                """, properties.maxTotalMessages());
    }

    private long cutoff() {
        return clock.instant().minus(properties.retention()).toEpochMilli();
    }
}
