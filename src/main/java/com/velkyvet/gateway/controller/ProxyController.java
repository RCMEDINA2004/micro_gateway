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

    @RequestMapping("/api/auth/**")
    public ResponseEntity<String> proxyAuth(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, authUrl, "/api");
    }

    @RequestMapping("/api/pets/**")
    public ResponseEntity<String> proxyPets(HttpServletRequest request,
                                            @RequestBody(required = false) String body) {
        return forward(request, body, petsUrl, "/api");
    }

    @RequestMapping("/api/appointments/**")
    public ResponseEntity<String> proxyAppointments(HttpServletRequest request,
                                                    @RequestBody(required = false) String body) {
        return forward(request, body, appointmentsUrl, "/api");
    }

    @RequestMapping("/api/agent/**")
    public ResponseEntity<String> proxyAgent(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        return forward(request, body, agentUrl, "/api");
    }

    @RequestMapping("/api/vaccines/**")
    public ResponseEntity<String> proxyVaccines(HttpServletRequest request,
                                                @RequestBody(required = false) String body) {
        return forward(request, body, vaccinesUrl, "/api");
    }

    private ResponseEntity<String> forward(HttpServletRequest request,
                                           String body,
                                           String serviceUrl,
                                           String prefix) {
        String originalPath = request.getRequestURI();
        String targetPath   = originalPath.replace(prefix, "");
        if (targetPath.isEmpty()) targetPath = "/";
        String queryString  = request.getQueryString();
        String targetUrl    = serviceUrl + targetPath + (queryString != null ? "?" + queryString : "");

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String lower = name.toLowerCase();
            // No copiar headers que causan conflicto al reenviar
            if (lower.equals("host") || lower.equals("content-length")
                    || lower.equals("connection") || lower.equals("accept-encoding")) {
                continue;
            }
            headers.set(name, request.getHeader(name));
        }

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

            // Solo poner body si hay contenido
            WebClient.ResponseSpec response;
            if (body != null && !body.isEmpty()) {
                response = spec.bodyValue(body).retrieve();
            } else {
                response = spec.retrieve();
            }

            ResponseEntity<String> microResponse = response
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> new RuntimeException(errorBody)))
                    .toEntity(String.class)
                    .block();

            // Devolver solo el body y el status, SIN copiar los headers del
            // microservicio (evita que el header CORS quede duplicado: "*, *")
            return ResponseEntity
                    .status(microResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(microResponse.getBody());

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("{")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).body(msg);
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Servicio no disponible: " + serviceUrl + "\"}");
        }
    }
}
