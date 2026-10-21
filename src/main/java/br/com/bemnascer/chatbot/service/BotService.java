package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.BemNascerProperties;
import br.com.bemnascer.chatbot.model.BotResponse;
import br.com.bemnascer.chatbot.model.Intent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BotService {

    private static final String FALLBACK = "Ainda não tenho essa informação cadastrada. " +
            "Para evitar passar uma orientação incorreta, confirme diretamente com a Maternidade Bem Nascer.";

    private final IntentService intentService;
    private final BemNascerProperties properties;

    public BotService(IntentService intentService, BemNascerProperties properties) {
        this.intentService = intentService;
        this.properties = properties;
    }

    public BotResponse answer(String message) {
        Intent intent = intentService.identify(message);
        return new BotResponse(intent, responseFor(intent));
    }

    public String responseFor(Intent intent) {
        return switch (intent) {
            case SAUDACAO -> "Olá! Sou o assistente virtual da " + properties.nome() + ". "
                    + "Posso ajudar com horários de visita e documentos para paciente e acompanhante.";
            case DOCUMENTOS_PACIENTE -> "Para atendimento na " + properties.nome() +
                    ", a paciente deve apresentar " + formatList(properties.documentos().paciente()) + ".";
            case DOCUMENTOS_ACOMPANHANTE -> "O acompanhante deve apresentar " +
                    formatList(properties.documentos().acompanhante()) + ".";
            case HORARIO_VISITA -> "O horário de visitas da " + properties.nome() + " é das " +
                    properties.visitas().inicio() + " às " + properties.visitas().fim() + ".";
            case REGRAS_VISITA -> "Regras de visita: " + String.join(" ", properties.visitas().regras());
            case HORARIO_ADMINISTRATIVO -> "O setor administrativo funciona das " +
                    properties.funcionamento().administrativoInicio() + " às " +
                    properties.funcionamento().administrativoFim() + ".";
            case ATENDIMENTO_24H -> hasText(properties.funcionamento().atendimento24h())
                    ? properties.funcionamento().atendimento24h()
                    : FALLBACK;
            case DESCONHECIDO -> "No momento, consigo orientar sobre documentos da paciente e do acompanhante, " +
                    "horário e regras de visita e horário do setor administrativo. " + FALLBACK;
        };
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "os documentos informados pela maternidade";
        }
        if (items.size() == 1) {
            return items.getFirst();
        }
        return items.subList(0, items.size() - 1).stream().collect(Collectors.joining(", ")) +
                " e " + items.getLast();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
