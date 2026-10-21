package br.com.bemnascer.chatbot.controller;

import br.com.bemnascer.chatbot.service.EvolutionClient;
import br.com.bemnascer.chatbot.service.EvolutionWebhookParser;
import br.com.bemnascer.chatbot.service.WebhookQueue;
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
    private final WebhookQueue queue;
    private final EvolutionClient evolutionClient;

    public WhatsAppWebhookController(EvolutionWebhookParser parser,
                                     WebhookQueue queue,
                                     EvolutionClient evolutionClient) {
        this.parser = parser;
        this.queue = queue;
        this.evolutionClient = evolutionClient;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook recebido em /webhook.");
        if (!parser.isMessagesUpsert(payload)) {
            log.info("Webhook ignorado; motivo=EVENT_NOT_SUPPORTED (esperado MESSAGES_UPSERT).");
            return ResponseEntity.ok().build();
        }

        var incoming = parser.extractIncomingMessage(payload);
        if (incoming.isEmpty()) return ResponseEntity.ok().build();
        boolean sendEnabled = evolutionClient.isSendEnabled();
        var admission = queue.enqueue(incoming.get(), sendEnabled);
        log.info("Webhook MESSAGES_UPSERT; resultado={}; envio_habilitado={}", admission, sendEnabled);
        if (admission == WebhookQueue.Admission.FULL) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
