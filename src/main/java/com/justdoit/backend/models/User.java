package com.justdoit.backend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome e obrigatorio")
    @Size(max = 120, message = "O nome deve ter no maximo 120 caracteres")
    @Column(nullable = false, length = 120)
    private String name;

    @NotBlank(message = "O e-mail e obrigatorio")
    @Email(message = "Formato de e-mail invalido")
    @Size(max = 255, message = "O e-mail deve ter no maximo 255 caracteres")
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "A senha e obrigatoria")
    @Size(min = 8, message = "A senha deve ter no minimo 8 caracteres")
    @Column(nullable = false, length = 255)
    private String password;
}
