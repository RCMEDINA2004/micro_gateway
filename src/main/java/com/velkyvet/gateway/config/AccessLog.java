package com.velkyvet.gateway.config;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

// Esta entidad guarda un log de cada petición que pasa por el gateway
// Es el motivo por el que el gateway tiene su propia base de datos
@Data
@Entity
@Table(name = "access_logs")
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;      // GET, POST, etc.
    private String path;        // /api/pets, /api/appointments, etc.
    private String userRole;    // rol del usuario que hizo la petición
    private Integer userId;     // ID del usuario
    private Integer status;     // código de respuesta HTTP

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
