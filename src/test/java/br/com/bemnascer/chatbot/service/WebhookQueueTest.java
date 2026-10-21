package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BotProperties;
import br.com.bemnascer.chatbot.config.EvolutionProperties;
import br.com.bemnascer.chatbot.controller.WhatsAppWebhookController;
import br.com.bemnascer.chatbot.model.BotResponse;
import br.com.bemnascer.chatbot.model.Intent;
import br.com.bemnascer.chatbot.service.EvolutionWebhookParser.IncomingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;

import static br.com.bemnascer.chatbot.service.WebhookQueue.Admission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookQueueTest {
    private final BotTestSupport.MutableClock clock = new BotTestSupport.MutableClock();
    private JdbcTemplate jdbc;
    private WebhookQueue queue;
    private EvolutionProperties evolution;

    @BeforeEach
    void setup() {
        jdbc = BotTestSupport.database();
        evolution = new EvolutionProperties("http://localhost:8080", "test-key", "MZN-LOCAL", true);
        queue = new WebhookQueue(jdbc, BotTestSupport.properties(), evolution, clock);
    }

    private IncomingMessage incoming(String id, String text) {
        return new IncomingMessage("MZN-LOCAL", id, BotTestSupport.NUMBER, text);
    }

    @Test
    void emptyAllowlistWrongNumberAndWrongInstanceNeverEnterQueue() {
        var blocked = new BotProperties(List.of(), Duration.ofDays(7), Duration.ofHours(24), 200, 2000, 12, 8000, 2000, 2000);
        assertThat(new WebhookQueue(jdbc, blocked, evolution, clock).enqueue(incoming("1", "oi"), true)).isEqualTo(IGNORED);
        assertThat(queue.enqueue(new IncomingMessage("other-instance", "1", BotTestSupport.NUMBER, "oi"), true)).isEqualTo(IGNORED);
        assertThat(queue.enqueue(new IncomingMessage("MZN-LOCAL", "1", "5596999990002", "oi"), true)).isEqualTo(IGNORED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_event", Long.class)).isZero();
    }

    @Test
    void duplicateIsIgnoredEvenAfterRestartAndMessagesKeepArrivalOrder() {
        assertThat(queue.enqueue(incoming("1", "primeira"), false)).isEqualTo(ACCEPTED);
        assertThat(queue.enqueue(incoming("2", "segunda"), false)).isEqualTo(ACCEPTED);
        var restarted = new WebhookQueue(jdbc, BotTestSupport.properties(), evolution, clock);
        assertThat(restarted.enqueue(incoming("1", "primeira"), false)).isEqualTo(DUPLICATE);
        assertThat(restarted.claimNext().orElseThrow().text()).isEqualTo("primeira");
        assertThat(restarted.claimNext().orElseThrow().text()).isEqualTo("segunda");
        assertThat(restarted.claimNext()).isEmpty();
    }

    @Test
    void oversizedInputDoesNotStoreWholePayload() {
        queue.enqueue(incoming("1", "x".repeat(2001)), false);
        var event = queue.claimNext().orElseThrow();
        assertThat(event.oversized()).isTrue();
        assertThat(event.text()).isEmpty();
    }

    @Test
    void restartRecoversProcessingButNeverRetriesUncertainDelivery() {
        queue.enqueue(incoming("1", "oi"), true);
        queue.enqueue(incoming("2", "documentos?"), true);
        var processing = queue.claimNext().orElseThrow();
        var sending = queue.claimNext().orElseThrow();
        queue.markSending(sending.key());
        queue.recover();
        assertThat(jdbc.queryForObject("SELECT event_status FROM bot_event WHERE event_key=?", String.class, sending.key()))
                .isEqualTo("UNCERTAIN");
        assertThat(queue.claimNext().orElseThrow().key()).isEqualTo(processing.key());
        assertThat(queue.claimNext()).isEmpty();
    }

    @Test
    void controllerAcknowledgesBeforeInferenceAndRejectsFullQueue() {
        var props = new BotProperties(List.of(BotTestSupport.NUMBER), Duration.ofDays(7), Duration.ofHours(24),
                200, 2000, 12, 8000, 2000, 1);
        var limitedQueue = new WebhookQueue(jdbc, props, evolution, clock);
        var sender = mock(EvolutionClient.class);
        var controller = new WhatsAppWebhookController(new EvolutionWebhookParser(), limitedQueue, sender);
        var first = EvolutionWebhookParserTest.payload(BotTestSupport.NUMBER + "@s.whatsapp.net", false, "1");
        assertThat(controller.webhook(first).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(first).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(EvolutionWebhookParserTest.payload(
                BotTestSupport.NUMBER + "@s.whatsapp.net", false, "2")).getStatusCode().value()).isEqualTo(503);
        verify(sender, never()).sendText(anyString(), anyString());
    }

    @Test
    void enablingSendingDoesNotSendEventsOriginallyAcceptedInSimulation() {
        queue.enqueue(incoming("1", "oi"), false);
        var bot = mock(ContextualBotService.class);
        when(bot.answer(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(new BotResponse(Intent.SAUDACAO, "Olá!"));
        var sender = mock(EvolutionClient.class);
        when(sender.isSendEnabled()).thenReturn(true);
        var worker = new WebhookWorker(queue, bot, new ConversationStore(jdbc, BotTestSupport.properties(), clock),
                sender, BotTestSupport.properties(), clock);
        worker.start();
        worker.processNext();
        verify(sender, never()).sendText(anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT event_status FROM bot_event", String.class)).isEqualTo("SIMULATED");
    }

    @Test
    void ambiguousSendFailureIsRecordedAndDuplicateDoesNotResend() {
        queue.enqueue(incoming("1", "oi"), true);
        var bot = mock(ContextualBotService.class);
        when(bot.answer(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(new BotResponse(Intent.SAUDACAO, "Olá!"));
        var sender = mock(EvolutionClient.class);
        when(sender.isSendEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("connection interrupted")).when(sender).sendText(anyString(), anyString());
        var worker = new WebhookWorker(queue, bot, new ConversationStore(jdbc, BotTestSupport.properties(), clock),
                sender, BotTestSupport.properties(), clock);
        worker.start();
        worker.processNext();
        assertThat(jdbc.queryForObject("SELECT event_status FROM bot_event", String.class)).isEqualTo("UNCERTAIN");
        assertThat(queue.enqueue(incoming("1", "oi"), true)).isEqualTo(DUPLICATE);
        worker.processNext();
        verify(sender, times(1)).sendText(anyString(), anyString());
    }

    @Test
    void undeliveredAnswerDoesNotBecomeModelContextAndOldEventIdsExpire() {
        queue.enqueue(incoming("1", "documentos?"), true);
        var event = queue.claimNext().orElseThrow();
        var store = new ConversationStore(jdbc, BotTestSupport.properties(), clock);
        store.appendExchange(event.conversation(), event.key(), event.text(), "Resposta não entregue", clock.instant());
        queue.finish(event.key(), "UNCERTAIN");
        assertThat(store.context(event.conversation(), "other", 0)).hasSize(1)
                .allSatisfy(message -> assertThat(message.role()).isEqualTo("user"));
        clock.advance(Duration.ofDays(8));
        store.cleanup();
        assertThat(store.context(event.conversation(), "other", 0)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bot_event", Long.class)).isZero();
    }
}
