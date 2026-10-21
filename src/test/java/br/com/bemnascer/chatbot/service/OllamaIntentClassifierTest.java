package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.model.Intent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaIntentClassifierTest {
    private final OllamaIntentClassifier classifier = new OllamaIntentClassifier(null);

    @Test
    void acceptsOnlyValidatedClassificationsAndRemovesDuplicates() {
        assertThat(classifier.decode("{\"intents\":[\"HORARIO_VISITA\",\"DOCUMENTOS_PACIENTE\",\"HORARIO_VISITA\"]}"))
                .containsExactly(Intent.HORARIO_VISITA, Intent.DOCUMENTOS_PACIENTE);
    }

    @Test
    void rejectsFreeTextInvalidJsonExtraFieldsAndOutOfScopeIntents() {
        for (String output : new String[] {
                "Tome um medicamento", "{}", "{\"intents\":[]}", "{\"intents\":[null]}",
                "{\"intents\":[\"REGRAS_VISITA\"]}", "{\"intents\":[\"HORARIO_VISITA\"],\"response\":\"às 3h\"}",
                "```json\n{\"intents\":[\"HORARIO_VISITA\"]}\n```"}) {
            assertThatThrownBy(() -> classifier.decode(output)).isInstanceOf(RuntimeException.class);
        }
    }
}
