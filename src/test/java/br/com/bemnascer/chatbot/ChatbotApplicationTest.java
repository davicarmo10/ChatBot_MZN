package br.com.bemnascer.chatbot;

import br.com.bemnascer.chatbot.config.BotProperties;
import br.com.bemnascer.chatbot.service.ContextualBotService;
import br.com.bemnascer.chatbot.model.BotRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-test;DB_CLOSE_DELAY=-1",
        "ai.enabled=false", "bot.allowed-numbers=", "evolution.send-enabled=false"
})
class ChatbotApplicationTest {
    @Autowired ContextualBotService bot;
    @Autowired BotProperties properties;
    @Autowired Validator validator;

    @Test
    void startsWithInitializedSchemaAndSendingBlockedByDefault() {
        assertThat(properties.allows("5596999990001")).isFalse();
        assertThat(bot.preview("bom dia", "startup").response()).contains("Sou o assistente virtual");
        assertThat(bot.preview("quais documentos preciso levar?", "startup").response()).contains("Cartão do SUS");
    }

    @Test
    void validatesOptionalConversationIdAndRequiredText() {
        assertThat(validator.validate(new BotRequest("oi", null))).isEmpty();
        assertThat(validator.validate(new BotRequest("oi", "teste-1_ok"))).isEmpty();
        assertThat(validator.validate(new BotRequest("oi", "../wa:contact"))).isNotEmpty();
        assertThat(validator.validate(new BotRequest("", "teste"))).isNotEmpty();
    }
}
