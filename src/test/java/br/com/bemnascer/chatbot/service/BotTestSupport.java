package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.AiProperties;
import br.com.bemnascer.chatbot.config.BemNascerProperties;
import br.com.bemnascer.chatbot.config.BotProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

final class BotTestSupport {
    static final String NUMBER = "5596999990001";

    static BotProperties properties() {
        return properties(200, 2000, 12, 8000);
    }

    static BotProperties properties(int contact, int total, int messages, int characters) {
        return new BotProperties(List.of(NUMBER), Duration.ofDays(7), Duration.ofHours(24),
                contact, total, messages, characters, 2000, 2000);
    }

    static AiProperties ai() {
        return new AiProperties(true, "http://localhost:11434", "qwen3:4b", Duration.ofSeconds(60));
    }

    static JdbcTemplate database() {
        return database("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    static JdbcTemplate database(String url) {
        var source = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        return new JdbcTemplate(source);
    }

    static BotService answers() {
        return new BotService(new IntentService(new TextNormalizer()), new BemNascerProperties(
                "Maternidade Bem Nascer",
                new BemNascerProperties.Documentos(List.of("RG", "CPF", "Cartão do SUS"), List.of("RG", "CPF")),
                new BemNascerProperties.Visitas("15h", "17h", List.of("Não levar comida.")),
                new BemNascerProperties.Funcionamento("8h", "18h", "")));
    }

    static final class MutableClock extends Clock {
        private Instant time = Instant.parse("2026-10-21T12:00:00Z");

        void advance(Duration duration) { time = time.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
        @Override public Instant instant() { return time; }
    }
}
