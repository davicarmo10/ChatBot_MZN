package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.model.Intent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IntentService {

    private final TextNormalizer textNormalizer;

    public IntentService(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    public Intent identify(String message) {
        String text = textNormalizer.normalize(message);

        if (text.isBlank()) {
            return Intent.DESCONHECIDO;
        }

        if (mentionsCompanion(text) && mentionsDocuments(text)) {
            return Intent.DOCUMENTOS_ACOMPANHANTE;
        }

        if (mentionsDocuments(text)) {
            return Intent.DOCUMENTOS_PACIENTE;
        }

        if (mentions24Hours(text)) {
            return Intent.ATENDIMENTO_24H;
        }

        if (mentionsAdministrativeHours(text)) {
            return Intent.HORARIO_ADMINISTRATIVO;
        }

        if (mentionsVisitRules(text)) {
            return Intent.REGRAS_VISITA;
        }

        if (mentionsVisitHours(text)) {
            return Intent.HORARIO_VISITA;
        }

        return Intent.DESCONHECIDO;
    }

    private boolean mentionsCompanion(String text) {
        return containsAny(text, List.of(
                "acompanhante", "marido", "esposo", "esposa", "companheiro", "companheira"
        ));
    }

    private boolean mentionsDocuments(String text) {
        return containsAny(text, List.of(
                "documento", "documentos", "documentacao", "rg", "cpf", "cartao do sus",
                "cartao sus", "o que levar", "o que preciso levar", "dar entrada", "internacao"
        ));
    }

    private boolean mentions24Hours(String text) {
        return containsAny(text, List.of(
                "24h", "24 horas", "vinte e quatro horas", "madrugada", "sempre aberto",
                "sempre funciona", "atendimento de madrugada"
        ));
    }

    private boolean mentionsAdministrativeHours(String text) {
        boolean administrative = containsAny(text, List.of(
                "administrativo", "administracao", "setor administrativo", "recepcao administrativa"
        ));
        boolean hours = containsAny(text, List.of(
                "horario", "funciona", "abre", "fecha", "atendimento"
        ));
        return administrative && hours;
    }

    private boolean mentionsVisitRules(String text) {
        return containsAny(text, List.of(
                "capacete", "comida", "alimento", "levar comida", "roupa", "troca de acompanhante",
                "pode levar", "nao pode levar", "regra da visita", "regras de visita"
        ));
    }

    private boolean mentionsVisitHours(String text) {
        boolean visit = containsAny(text, List.of(
                "visita", "visitar", "visitante", "ver a paciente", "ver o bebe", "ver o bebê"
        ));
        boolean hours = containsAny(text, List.of(
                "horario", "hora", "que horas", "quando", "entra", "entrada", "sair", "termina"
        ));
        return visit && hours;
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }
}
