package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.EvolutionProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class EvolutionClient {

    private final EvolutionProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public EvolutionClient(EvolutionProperties properties) {
        this.properties = properties;
    }

    public boolean isSendEnabled() {
        return properties.sendEnabled()
                && hasText(properties.baseUrl())
                && hasText(properties.apiKey())
                && hasText(properties.instance());
    }

    public void sendText(String number, String text) {
        if (!isSendEnabled()) {
            return;
        }

        String url = stripTrailingSlash(properties.baseUrl()) + "/message/sendText/" + properties.instance();
        String body = "{\"number\":\"" + escapeJson(number) + "\",\"text\":\"" + escapeJson(text) + "\"}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("apikey", properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Evolution API retornou HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio para a Evolution API foi interrompido.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Falha de comunicação com a Evolution API.", e);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
