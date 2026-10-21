package br.com.bemnascer.chatbot.model;

import java.time.Instant;

public record HistoryMessage(String role, String content, Instant createdAt) {}
