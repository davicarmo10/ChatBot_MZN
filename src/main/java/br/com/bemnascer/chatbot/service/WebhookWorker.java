package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class WebhookWorker {
    private static final Logger log = LoggerFactory.getLogger(WebhookWorker.class);
    private final WebhookQueue queue;
    private final ContextualBotService bot;
    private final ConversationStore store;
    private final EvolutionClient evolution;
    private final BotProperties properties;
    private final Clock clock;
    private volatile boolean ready;

    public WebhookWorker(WebhookQueue queue, ContextualBotService bot, ConversationStore store,
                         EvolutionClient evolution, BotProperties properties, Clock clock) {
        this.queue = queue;
        this.bot = bot;
        this.store = store;
        this.evolution = evolution;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        queue.recover();
        store.cleanup();
        ready = true;
        log.info("Automação pronta: {} contato(s) configurado(s); envio={}",
                properties.allowedNumbers().size(), evolution.isSendEnabled());
    }

    @Scheduled(fixedDelay = 1000)
    public void processNext() {
        if (!ready) return;
        queue.claimNext().ifPresent(event -> {
            if (!queue.allowed(event.instance(), event.number())
                    || event.receivedAt().isBefore(clock.instant().minus(properties.retention()))) {
                queue.finish(event.key(), "IGNORED");
                return;
            }
            boolean sending = false;
            try {
                var answer = bot.answer(event.conversation(), event.key(), event.text(), event.oversized(), event.receivedAt());
                if (event.sendRequested() && evolution.isSendEnabled()) {
                    queue.markSending(event.key());
                    sending = true;
                    evolution.sendText(event.number(), answer.response());
                    queue.finish(event.key(), "SENT");
                } else {
                    queue.finish(event.key(), "SIMULATED");
                }
                log.info("Evento {} processado; intenção={}; envio={}", event.key().substring(0, 12), answer.intent(), sending);
            } catch (RuntimeException failure) {
                queue.finish(event.key(), sending ? "UNCERTAIN" : "FAILED");
                String code = failure instanceof EvolutionClient.DeliveryException delivery
                        ? delivery.code() : failure.getClass().getSimpleName();
                log.warn("Evento {} falhou; envio_incerto={}; erro={}", event.key().substring(0, 12), sending, code);
            }
        });
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void cleanup() {
        if (ready) store.cleanup();
    }
}
