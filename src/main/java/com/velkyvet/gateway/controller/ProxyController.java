package com.velkyvet.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    @Value("${services.auth}")
    private String authUrl;

    @Value("${services.pets}")
    private String petsUrl;

    @Value("${services.appointments}")
    private String appointmentsUrl;

    @Value("${services.agent}")
    private String agentUrl;

    @Value("${services.vaccines}")
    private String vaccinesUrl;

    private final WebClient.Builder webClientBuilder;

    // Timeout para cada llamada a un microservicio (10 segundos)
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Headers que nunca se reenvían al microservicio
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "host", "content-length", "connection",
            "accept-encoding", "transfer-encoding"
    );

    @RequestMapping("/api/auth/**")
    public ResponseEntity<String> proxyAuth(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, authUrl);
    }

    @RequestMapping("/api/pets/**")
    public ResponseEntity<String> proxyPets(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, petsUrl);
    }

    @RequestMapping("/api/appointments/**")
    public ResponseEntity<String> proxyAppointments(HttpServletRequest request,
                                                    @RequestBody(required = false) String body) {
        return forward(request, body, appointmentsUrl);
    }

    @RequestMapping("/api/agent/**")
    public ResponseEntity<String> proxyAgent(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        return forward(request, body, agentUrl);
    }

    @RequestMapping("/api/vaccines/**")
    public ResponseEntity<String> proxyVaccines(HttpServletRequest request,
                                                @RequestBody(required = false) String body) {
        return forward(request, body, vaccinesUrl);
    }

    private ResponseEntity<String> forward(HttpServletRequest request,
                                           String body,
                                           String serviceUrl) {
        // Construir la URL destino: quitar solo el prefijo "/api" del inicio
        String originalPath = request.getRequestURI();
        String targetPath = originalPath.startsWith("/api")
                ? originalPath.substring(4)   // elimina exactamente los 4 chars de "/api"
                : originalPath;
        if (targetPath.isEmpty()) targetPath = "/";

        String queryString = request.getQueryString();
        String targetUrl = serviceUrl + targetPath
                + (queryString != null ? "?" + queryString : "");

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Copiar headers seguros del request original
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!BLOCKED_HEADERS.contains(name.toLowerCase())) {
                headers.set(name, request.getHeader(name));
            }
        }

        // Inyectar datos del usuario autenticado (los pone JwtFilter como atributos)
        Object userId    = request.getAttribute("X-User-Id");
        Object userRole  = request.getAttribute("X-User-Role");
        Object userEmail = request.getAttribute("X-User-Email");
        if (userId != null)    headers.set("X-User-Id",    userId.toString());
        if (userRole != null)  headers.set("X-User-Role",  userRole.toString());
        if (userEmail != null) headers.set("X-User-Email", userEmail.toString());

        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            WebClient.RequestBodySpec spec = webClientBuilder.build()
                    .method(method)
                    .uri(targetUrl)
                    .headers(h -> h.addAll(headers));

            WebClient.ResponseSpec responseSpec = (body != null && !body.isEmpty())
                    ? spec.bodyValue(body).retrieve()
                    : spec.retrieve();

            // NO convertir errores HTTP en excepciones:
            // el status 4xx/5xx del microservicio se reenvía tal cual al cliente.
            // Solo llegará al catch si hay un error de RED (timeout, DNS, etc.)
            ResponseEntity<String> microResponse = responseSpec
                    .onStatus(status -> true, clientResponse -> Mono.empty())
                    .toEntity(String.class)
                    .timeout(TIMEOUT)
                    .block();

            if (microResponse == null) {
                return errorResponse(serviceUrl, "Respuesta vacía del microservicio");
            }

            return ResponseEntity
                    .status(microResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(microResponse.getBody());

        } catch (WebClientResponseException e) {
            // Error HTTP explícito del microservicio (raro con onStatus anterior, pero por si acaso)
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());

        } catch (Exception e) {
            // Error de red real: timeout, DNS, conexión rechazada
            return errorResponse(serviceUrl, e.getMessage());
        }
    }

    private ResponseEntity<String> errorResponse(String serviceUrl, String detail) {
        // No exponer la URL interna del microservicio al cliente
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"Servicio temporalmente no disponible\"}");
    }
}
