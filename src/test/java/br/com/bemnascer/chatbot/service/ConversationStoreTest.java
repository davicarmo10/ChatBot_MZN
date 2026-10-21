package br.com.bemnascer.chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationStoreTest {
    private final BotTestSupport.MutableClock clock = new BotTestSupport.MutableClock();

    @Test
    void fileHistorySurvivesClosingAndReopeningDatabase(@TempDir Path folder) {
        String url = "jdbc:h2:file:" + folder.resolve("history");
        var jdbc = BotTestSupport.database(url);
        var store = new ConversationStore(jdbc, BotTestSupport.properties(), clock);
        store.appendExchange("alice", "1", "documentos?", "RG e CPF", clock.instant());
        jdbc.execute("SHUTDOWN");
        var reopenedJdbc = BotTestSupport.database(url);
        var reopened = new ConversationStore(reopenedJdbc, BotTestSupport.properties(), clock);
        assertThat(reopened.context("alice", "none", 0)).hasSize(2);
        assertThat(reopened.context("bob", "none", 0)).isEmpty();
        reopenedJdbc.execute("SHUTDOWN");
    }

    @Test
    void cleanupExpiresRecordsIndividuallyAfterSevenDays() {
        var jdbc = BotTestSupport.database();
        var store = new ConversationStore(jdbc, BotTestSupport.properties(), clock);
        store.appendExchange("alice", "old", "antigo", "resposta antiga", clock.instant());
        clock.advance(Duration.ofDays(6));
        store.appendExchange("alice", "recent", "recente", "resposta recente", clock.instant());
        clock.advance(Duration.ofDays(2));
        store.cleanup();
        assertThat(store.context("alice", "none", 0)).hasSize(2)
                .allSatisfy(message -> assertThat(message.content()).doesNotContain("antig"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_history", Long.class)).isEqualTo(2);
    }

    @Test
    void capsMessagesPerContactAndAcrossAllContacts() {
        var jdbc = BotTestSupport.database();
        var store = new ConversationStore(jdbc, BotTestSupport.properties(4, 6, 12, 8000), clock);
        for (int i = 0; i < 5; i++) store.appendExchange("alice", "a" + i, "q", "a", clock.instant());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_history WHERE conversation_key='alice'", Long.class)).isEqualTo(4);
        for (int i = 0; i < 5; i++) store.appendExchange("bob", "b" + i, "q", "a", clock.instant());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_history", Long.class)).isEqualTo(6);
    }

    @Test
    void contextUsesOnlyRecentMessagesWithinCharacterBudget() {
        var store = new ConversationStore(BotTestSupport.database(), BotTestSupport.properties(), clock);
        for (int i = 0; i < 10; i++) {
            store.appendExchange("alice", "event-" + i, "u".repeat(1000), "a".repeat(1000), clock.instant());
        }
        var context = store.context("alice", "none", 2000);
        assertThat(context).hasSizeLessThanOrEqualTo(12);
        assertThat(context.stream().mapToInt(entry -> entry.content().length()).sum() + 2000).isLessThanOrEqualTo(8000);
        assertThat(context.getFirst().role()).isEqualTo("user");
    }
}
