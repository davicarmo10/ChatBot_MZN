package br.com.bemnascer.chatbot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BotRequest(
        @NotBlank(message = "A mensagem é obrigatória.") String message,
        @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}", message = "conversationId deve ter de 1 a 64 letras, números, _ ou -.")
        String conversationId) {}
