package com.velkyvet.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;

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

    // El AI Agent vive en un AWS API Gateway que solo acepta POST en la raíz /prod/.
    // No le agregamos la subruta /agent/chat: mandamos el body directo a la raíz.
    @RequestMapping("/api/agent/**")
    public ResponseEntity<String> proxyAgent(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> microResponse = restTemplate.exchange(
                    agentUrl, HttpMethod.POST, httpEntity, String.class);

            return ResponseEntity
                    .status(microResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(microResponse.getBody());

        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Servicio de IA no disponible\"}");
        }
    }

    @RequestMapping("/api/vaccines/**")
    public ResponseEntity<String> proxyVaccines(HttpServletRequest request,
                                                @RequestBody(required = false) String body) {
        return forward(request, body, vaccinesUrl);
    }

    private ResponseEntity<String> forward(HttpServletRequest request,
                                           String body,
                                           String serviceUrl) {
        // Quitar el prefijo "/api" del inicio. Quitar barra final del serviceUrl
        // para evitar dobles barras (//)
        String base = serviceUrl.endsWith("/")
                ? serviceUrl.substring(0, serviceUrl.length() - 1)
                : serviceUrl;

        String originalPath = request.getRequestURI();
        String targetPath = originalPath.startsWith("/api")
                ? originalPath.substring(4)
                : originalPath;
        if (targetPath.isEmpty()) targetPath = "/";

        String queryString = request.getQueryString();
        String targetUrl = base + targetPath
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

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> microResponse = restTemplate.exchange(
                    targetUrl, method, httpEntity, String.class);

            return ResponseEntity
                    .status(microResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(microResponse.getBody());

        } catch (RestClientResponseException e) {
            // El microservicio respondió con error HTTP (4xx/5xx).
            // Reenviamos el status y body TAL CUAL al cliente (no lo convertimos en 502)
            return ResponseEntity
                    .status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());

        } catch (ResourceAccessException e) {
            // Error de RED real: timeout, DNS, conexión rechazada
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Servicio temporalmente no disponible\"}");

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Error en el gateway\"}");
        }
    }
}
