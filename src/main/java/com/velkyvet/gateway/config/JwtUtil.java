package com.velkyvet.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    // Obtener la clave de firma a partir del secreto
    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Extraer todos los claims (datos) del token
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Verificar si el token es válido (no expirado y firma correcta)
    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Extraer el rol del usuario desde el token
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    // Extraer el ID del usuario desde el token
    public Integer extractUserId(String token) {
        return extractClaims(token).get("user_id", Integer.class);
    }

    // Extraer el email del usuario desde el token
    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }
}
