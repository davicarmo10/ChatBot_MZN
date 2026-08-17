package br.com.bemnascer.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatbotBemNascerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotBemNascerApplication.class, args);
    }
}
