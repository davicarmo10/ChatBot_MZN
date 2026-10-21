package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.AiProperties;
import br.com.bemnascer.chatbot.config.BotProperties;
import br.com.bemnascer.chatbot.model.BotResponse;
import br.com.bemnascer.chatbot.model.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ContextualBotService {
    private static final Logger log = LoggerFactory.getLogger(ContextualBotService.class);
    private static final String UNKNOWN = "Ainda não tenho essa informação cadastrada. "
            + "Posso ajudar com horário de visitas e documentos para paciente e acompanhante. "
            + "Se sua dúvida for sobre esses assuntos, pode reformular a pergunta? "
            + "Para outros assuntos, confirme diretamente com a Maternidade Bem Nascer.";
    private static final String TOO_LONG = "Sua mensagem ficou muito longa. Pode resumir sua dúvida em até 2.000 caracteres?";

    private final IntentClassifier classifier;
    private final IntentService rules;
    private final BotService answers;
    private final TextNormalizer normalizer;
    private final ConversationStore store;
    private final BotProperties properties;
    private final AiProperties ai;
    private final Clock clock;

    public ContextualBotService(IntentClassifier classifier, IntentService rules, BotService answers,
                                TextNormalizer normalizer, ConversationStore store,
                                BotProperties properties, AiProperties ai, Clock clock) {
        this.classifier = classifier;
        this.rules = rules;
        this.answers = answers;
        this.normalizer = normalizer;
        this.store = store;
        this.properties = properties;
        this.ai = ai;
        this.clock = clock;
    }

    public BotResponse preview(String message, String conversationId) {
        String id = conversationId == null ? UUID.randomUUID().toString() : conversationId;
        return answer("test:" + id, "test-event:" + UUID.randomUUID(), message, false, clock.instant());
    }

    // One inference at a time on this CPU; webhook admission is independent and stays fast.
    public synchronized BotResponse answer(String conversation, String source, String message,
                                            boolean oversized, Instant receivedAt) {
        BotResponse answer;
        if (oversized || message.length() > properties.maxTextCharacters()) {
            answer = new BotResponse(Intent.DESCONHECIDO, TOO_LONG);
        } else {
            String normalized = normalizer.normalize(message);
            var greeting = GreetingTrigger.match(normalized);
            String prefix = "";
            if (greeting.isPresent()) {
                String salutation = greeting.get().salutation();
                if (greeting.get().standalone()) {
                    boolean introduce = store.lastIncoming(conversation, source)
                            .map(last -> !receivedAt.isBefore(last.plus(properties.greetingIdle()))).orElse(true);
                    String text = introduce ? salutation + " " + answers.responseFor(Intent.SAUDACAO)
                            .replaceFirst("^Olá! ", "") : salutation + " Como posso ajudar?";
                    answer = new BotResponse(Intent.SAUDACAO, text);
                    store.appendExchange(conversation, source, message, answer.response(), receivedAt);
                    return answer;
                }
                prefix = salutation + " ";
            }
            List<Intent> intents;
            try {
                if (!ai.enabled()) throw new IllegalStateException("IA desativada");
                intents = classifier.classify(message, store.context(conversation, source, message.length()));
                if (intents == null || intents.isEmpty() || !OllamaIntentClassifier.SUPPORTED.containsAll(intents)) {
                    throw new IllegalArgumentException("Classificação inválida");
                }
            } catch (RuntimeException failure) {
                // Exception messages from HTTP/model clients can contain private conversation data.
                log.warn("Classificação local indisponível; usando regras. tipo={}", failure.getClass().getSimpleName());
                intents = fallback(message);
            }
            String text = prefix + String.join("\n\n", intents.stream().distinct()
                    .map(intent -> intent == Intent.DESCONHECIDO ? UNKNOWN : answers.responseFor(intent)).toList());
            answer = new BotResponse(intents.getFirst(), text);
        }
        store.appendExchange(conversation, source, oversized || message.length() > properties.maxTextCharacters()
                ? "[Mensagem acima do limite; solicitada reformulação]" : message, answer.response(), receivedAt);
        return answer;
    }

    private List<Intent> fallback(String message) {
        // Split independent questions so a clear compound question still works without the model.
        List<Intent> found = new ArrayList<>();
        for (String part : message.split("(?i)[?;]|\\s+e\\s+(?=qual|quais|que|o horario)")) {
            String normalized = normalizer.normalize(part);
            if (normalized.matches(".*\\b(domingo|segunda|terca|quarta|quinta|sexta|sabado|feriado)\\b.*")) {
                found.add(Intent.DESCONHECIDO);
                continue;
            }
            Intent intent = rules.identify(part);
            if (OllamaIntentClassifier.SUPPORTED.contains(intent) && intent != Intent.DESCONHECIDO) found.add(intent);
        }
        return found.isEmpty() ? List.of(Intent.DESCONHECIDO) : found.stream().distinct().toList();
    }
}
