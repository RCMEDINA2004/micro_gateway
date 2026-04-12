package com.velkyvet.gateway.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.velkyvet.gateway.config.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // Rutas públicas que NO necesitan token
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Si es ruta pública, dejar pasar sin validar token
        if (PUBLIC_ROUTES.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Buscar token en header Authorization o en cookie
        String token = extractToken(request);

        if (token == null || !jwtUtil.isValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Token requerido o inválido\"}");
            return;
        }

        // Verificar roles según la ruta
        String role = jwtUtil.extractRole(token);
        if (!hasAccess(path, request.getMethod(), role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"Acceso denegado para rol: " + role + "\"}");
            return;
        }

        // Inyectar datos del usuario como headers para los microservicios
        // Usamos HttpServletRequestWrapper para añadir headers customizados
        request.setAttribute("X-User-Id", jwtUtil.extractUserId(token));
        request.setAttribute("X-User-Role", role);
        request.setAttribute("X-User-Email", jwtUtil.extractEmail(token));

        filterChain.doFilter(request, response);
    }

    // Extraer token del header Authorization o de la cookie
    private String extractToken(HttpServletRequest request) {
        // 1. Buscar en header: Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. Buscar en cookie: access_token
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> "access_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    // Verificar si el rol tiene acceso a la ruta y método
    private boolean hasAccess(String path, String method, String role) {
        // ADMIN tiene acceso a todo
        if ("ADMIN".equals(role)) return true;

        // VET puede ver mascotas y gestionar citas
        if ("VET".equals(role)) {
            if (path.startsWith("/api/pets")) return true;
            if (path.startsWith("/api/appointments")) return true;
            return false;
        }

        // CLIENT solo puede ver sus propias mascotas y citas
        if ("CLIENT".equals(role)) {
            if (path.startsWith("/api/pets/my")) return true;
            if (path.equals("/api/pets") && "POST".equals(method)) return true;
            if (path.startsWith("/api/appointments/my")) return true;
            if (path.equals("/api/appointments") && "POST".equals(method)) return true;
            if (path.startsWith("/api/auth/me")) return true;
            return false;
        }

        return false;
    }
}
