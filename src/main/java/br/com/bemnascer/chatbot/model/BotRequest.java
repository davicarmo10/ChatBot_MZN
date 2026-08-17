package br.com.bemnascer.chatbot.model;

import jakarta.validation.constraints.NotBlank;

public record BotRequest(@NotBlank(message = "A mensagem é obrigatória.") String message) {}
