package br.com.fiap.agendafit.dto;

public record LoginResponse(String token, long expiresIn) {
}
