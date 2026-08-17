package br.com.bemnascer.chatbot.controller;

import br.com.bemnascer.chatbot.model.BotRequest;
import br.com.bemnascer.chatbot.model.BotResponse;
import br.com.bemnascer.chatbot.service.BotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final BotService botService;

    public BotController(BotService botService) {
        this.botService = botService;
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("status", "ok", "service", "chatbot-bem-nascer");
    }

    @PostMapping("/test")
    public BotResponse test(@Valid @RequestBody BotRequest request) {
        return botService.answer(request.message());
    }
}
