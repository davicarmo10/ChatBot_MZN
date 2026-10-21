package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.model.HistoryMessage;
import br.com.bemnascer.chatbot.model.Intent;

import java.util.List;

public interface IntentClassifier {
    List<Intent> classify(String message, List<HistoryMessage> history);
}
