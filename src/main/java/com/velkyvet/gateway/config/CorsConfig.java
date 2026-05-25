package com.velkyvet.gateway.config;

import org.springframework.context.annotation.Configuration;

// El manejo de CORS ahora se hace en JwtFilter (headers en todas las respuestas).
// Esta clase se deja vacia para no romper imports existentes.
@Configuration
public class CorsConfig {
}
