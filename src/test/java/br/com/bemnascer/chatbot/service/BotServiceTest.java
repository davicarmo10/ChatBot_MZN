package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BemNascerProperties;
import br.com.bemnascer.chatbot.model.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotServiceTest {

    private BotService service;

    @BeforeEach
    void setUp() {
        var properties = new BemNascerProperties(
                "Maternidade Bem Nascer",
                new BemNascerProperties.Documentos(
                        List.of("RG", "CPF", "Cartão do SUS"),
                        List.of("RG", "CPF")
                ),
                new BemNascerProperties.Visitas(
                        "15h", "17h",
                        List.of(
                                "Não é permitido entrar com capacete.",
                                "Não é permitido levar comida.",
                                "Na troca de acompanhante, deve ser levada roupa para a paciente."
                        )
                ),
                new BemNascerProperties.Funcionamento("8h", "18h", "")
        );
        service = new BotService(new IntentService(new TextNormalizer()), properties);
    }

    @Test
    void shouldAnswerVisitHours() {
        var response = service.answer("Qual o horário de visita?");
        assertThat(response.intent()).isEqualTo(Intent.HORARIO_VISITA);
        assertThat(response.response()).contains("15h", "17h");
    }

    @Test
    void shouldAnswerPatientDocuments() {
        var response = service.answer("Quais documentos preciso levar?");
        assertThat(response.response()).contains("RG", "CPF", "Cartão do SUS");
    }

    @Test
    void shouldFallbackForUnknown24HourInformation() {
        var response = service.answer("Funciona 24 horas?");
        assertThat(response.response()).contains("Ainda não tenho essa informação cadastrada");
    }
}
