package br.com.fiap.agendafit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "name is required") @Size(max = 255, message = "name must have at most 255 characters") String name,
        @NotBlank(message = "email is required") @Email(message = "email must be valid")
        @Size(max = 255, message = "email must have at most 255 characters") String email,
        @NotBlank(message = "password is required") String password) {
}
