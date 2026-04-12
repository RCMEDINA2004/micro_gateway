package com.velkyvet.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Enumeration;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    // URLs de los microservicios (vienen de application.properties)
    @Value("${services.auth}")
    private String authUrl;

    @Value("${services.pets}")
    private String petsUrl;

    @Value("${services.appointments}")
    private String appointmentsUrl;

    private final WebClient.Builder webClientBuilder;

    // ── AUTH: /api/auth/** → auth-service ──────────────────────────
    @RequestMapping("/api/auth/**")
    public ResponseEntity<String> proxyAuth(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, authUrl, "/api/auth");
    }

    // ── PETS: /api/pets/** → pets-service ──────────────────────────
    @RequestMapping("/api/pets/**")
    public ResponseEntity<String> proxyPets(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, petsUrl, "/api/pets");
    }

    // ── APPOINTMENTS: /api/appointments/** → appointments-service ──
    @RequestMapping("/api/appointments/**")
    public ResponseEntity<String> proxyAppointments(HttpServletRequest request,
                                                    @RequestBody(required = false) String body) {
        return forward(request, body, appointmentsUrl, "/api/appointments");
    }

    // ── Método genérico para reenviar cualquier petición ──────────
    private ResponseEntity<String> forward(HttpServletRequest request,
                                           String body,
                                           String serviceUrl,
                                           String prefix) {
        // Construir la URL destino: quitar /api/xxx del path
        String originalPath = request.getRequestURI();
        String targetPath   = originalPath.replace(prefix, "");
        String queryString  = request.getQueryString();
        String targetUrl    = serviceUrl + targetPath + (queryString != null ? "?" + queryString : "");

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Copiar headers originales + añadir los del usuario (seteados por JwtFilter)
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.set(name, request.getHeader(name));
        }

        // Añadir headers con datos del usuario para que el microservicio los use
        Object userId = request.getAttribute("X-User-Id");
        Object userRole = request.getAttribute("X-User-Role");
        Object userEmail = request.getAttribute("X-User-Email");
        if (userId != null)    headers.set("X-User-Id",    userId.toString());
        if (userRole != null)  headers.set("X-User-Role",  userRole.toString());
        if (userEmail != null) headers.set("X-User-Email", userEmail.toString());

        headers.setContentType(MediaType.APPLICATION_JSON);

        // Reenviar la petición al microservicio con WebClient (async/reactivo)
        try {
            return webClientBuilder.build()
                    .method(method)
                    .uri(targetUrl)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(body != null ? body : "")
                    .retrieve()
                    .toEntity(String.class)
                    .block(); // block() convierte el async en síncrono para simplificar
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\": \"Servicio no disponible\"}");
        }
    }
}
