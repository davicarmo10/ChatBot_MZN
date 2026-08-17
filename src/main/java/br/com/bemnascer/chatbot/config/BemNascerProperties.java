package br.com.bemnascer.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "bem-nascer")
public record BemNascerProperties(
        String nome,
        Documentos documentos,
        Visitas visitas,
        Funcionamento funcionamento
) {
    public record Documentos(List<String> paciente, List<String> acompanhante) {}

    public record Visitas(String inicio, String fim, List<String> regras) {}

    public record Funcionamento(String administrativoInicio,
                                String administrativoFim,
                                String atendimento24h) {}
}
