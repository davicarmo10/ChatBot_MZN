package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.model.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentServiceTest {

    private IntentService service;

    @BeforeEach
    void setUp() {
        service = new IntentService(new TextNormalizer());
    }

    @Test
    void shouldIdentifyPatientDocuments() {
        assertThat(service.identify("Quais documentos preciso levar para a internação?"))
                .isEqualTo(Intent.DOCUMENTOS_PACIENTE);
    }

    @Test
    void shouldIdentifyCompanionDocuments() {
        assertThat(service.identify("Quais documentos meu acompanhante precisa levar?"))
                .isEqualTo(Intent.DOCUMENTOS_ACOMPANHANTE);
    }

    @Test
    void shouldIdentifyVisitHours() {
        assertThat(service.identify("Que horas posso visitar a paciente?"))
                .isEqualTo(Intent.HORARIO_VISITA);
    }

    @Test
    void shouldIdentifyVisitRules() {
        assertThat(service.identify("Posso levar comida na visita?"))
                .isEqualTo(Intent.REGRAS_VISITA);
    }

    @Test
    void shouldIdentifyAdministrativeHours() {
        assertThat(service.identify("Qual o horário do setor administrativo?"))
                .isEqualTo(Intent.HORARIO_ADMINISTRATIVO);
    }

    @Test
    void shouldNotInvent24HourService() {
        assertThat(service.identify("A maternidade funciona 24 horas?"))
                .isEqualTo(Intent.ATENDIMENTO_24H);
    }
}
