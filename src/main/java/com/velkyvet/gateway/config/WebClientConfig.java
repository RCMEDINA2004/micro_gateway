package com.velkyvet.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // Bean de WebClient para hacer peticiones HTTP a los microservicios
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
