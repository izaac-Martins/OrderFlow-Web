package com.orderflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload de login compatível com {@code openapi.yaml} ({@code login} ou {@code email}),
 * permitindo informar opcionalmente {@code lojaId} ou {@code codigoLoja} em ambientes multi-loja.
 */
public record LoginRequest(
        @NotBlank(message = "Credencial de login/email é obrigatória")
        @Size(min = 3, max = 100, message = "Login/email deve ter entre 3 e 100 caracteres")
        @JsonAlias({"email", "username"})
        String login,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 128, message = "Senha deve ter entre 6 e 128 caracteres")
        String senha,

        @NotBlank(message = "Identificador do dispositivo é obrigatório")
        @Size(max = 80, message = "Identificador do dispositivo deve ter no máximo 80 caracteres")
        String dispositivo,

        UUID lojaId,
        String codigoLoja
) {
}
