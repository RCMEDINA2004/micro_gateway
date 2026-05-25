package com.velkyvet.gateway.filter;

import com.velkyvet.gateway.config.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "velkyvet_super_secret_test_key_32chars!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    private String buildToken(String role, String email, Long userId) {
        byte[] keyBytes = SECRET.getBytes();
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .claim("role", role)
                .claim("email", email)
                .claim("user_id", userId)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void isValid_tokenValido_retornaTrue() {
        String token = buildToken("CLIENT", "test@test.com", 1L);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_tokenInvalido_retornaFalse() {
        assertThat(jwtUtil.isValid("token.invalido.aqui")).isFalse();
    }

    @Test
    void isValid_tokenNulo_retornaFalse() {
        assertThat(jwtUtil.isValid(null)).isFalse();
    }

    @Test
    void extractRole_retornaRolCorrecto() {
        String token = buildToken("ADMIN", "admin@test.com", 99L);
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void extractEmail_retornaEmailCorrecto() {
        String token = buildToken("VET", "vet@test.com", 5L);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("vet@test.com");
    }

    @Test
    void extractUserId_retornaIdCorrecto() {
        String token = buildToken("CLIENT", "client@test.com", 42L);
        Integer userId = jwtUtil.extractUserId(token);
        assertThat(userId).isNotNull();
    }
}
