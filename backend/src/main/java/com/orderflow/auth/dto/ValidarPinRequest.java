package com.orderflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload para validação do PIN de alçada gerencial (4 a 6 dígitos numéricos).
 */
public record ValidarPinRequest(
        @NotBlank(message = "PIN de aprovação é obrigatório")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "O PIN de alçada deve conter de 4 a 6 dígitos numéricos")
        @JsonAlias({"pinAprovador"})
        String pin,

        @Size(max = 120, message = "Descrição da operação deve ter no máximo 120 caracteres")
        String operacao
) {
}
