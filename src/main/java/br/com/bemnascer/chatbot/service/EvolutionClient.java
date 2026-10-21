package br.com.bemnascer.chatbot.service;

import br.com.bemnascer.chatbot.config.EvolutionProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EvolutionClient {

    private final EvolutionProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

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

        String url = stripTrailingSlash(properties.baseUrl()) + "/message/sendText/"
                + URLEncoder.encode(properties.instance(), StandardCharsets.UTF_8).replace("+", "%20");
        String body = mapper.writeValueAsString(Map.of("number", number, "text", text));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("apikey", properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("HTTP_" + response.statusCode(), null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("INTERRUPTED", e);
        } catch (IOException e) {
            throw new DeliveryException("IO_" + e.getClass().getSimpleName(), e);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class DeliveryException extends IllegalStateException {
        private final String code;

        public DeliveryException(String code, Throwable cause) {
            super("Falha no envio para a Evolution API: " + code, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
