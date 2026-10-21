package br.com.bemnascer.chatbot.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import br.com.bemnascer.chatbot.service.IntentService;
import br.com.bemnascer.chatbot.service.TextNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;

@Configuration
public class AiConfiguration {
    @Bean
    public TextNormalizer textNormalizer() {
        return new TextNormalizer();
    }

    @Bean
    public IntentService intentService(TextNormalizer normalizer) {
        return new IntentService(normalizer);
    }

    @Bean
    public Clock botClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ChatModel chatModel(AiProperties properties) {
        return OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .temperature(0.0)
                .think(false)
                .numCtx(8192)
                .numPredict(128)
                .responseFormat(JSON)
                .timeout(properties.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
