package br.com.bemnascer.chatbot.controller;

import br.com.bemnascer.chatbot.model.BotResponse;
import br.com.bemnascer.chatbot.service.BotService;
import br.com.bemnascer.chatbot.service.EvolutionClient;
import br.com.bemnascer.chatbot.service.EvolutionWebhookParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final EvolutionWebhookParser parser;
    private final BotService botService;
    private final EvolutionClient evolutionClient;

    public WhatsAppWebhookController(EvolutionWebhookParser parser,
                                     BotService botService,
                                     EvolutionClient evolutionClient) {
        this.parser = parser;
        this.botService = botService;
        this.evolutionClient = evolutionClient;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        if (!parser.isMessagesUpsert(payload)) {
            return ResponseEntity.ok().build();
        }

        parser.extractIncomingMessage(payload).ifPresent(message -> {
            BotResponse answer = botService.answer(message.text());
            log.info("Mensagem recebida de {} ({}): {} -> {}",
                    message.pushName(), message.number(), message.text(), answer.intent());

            if (evolutionClient.isSendEnabled()) {
                evolutionClient.sendText(message.number(), answer.response());
            } else {
                log.info("EVOLUTION_SEND_ENABLED=false. Resposta simulada: {}", answer.response());
            }
        });

        return ResponseEntity.ok().build();
    }
}
