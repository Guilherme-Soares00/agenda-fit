package br.com.fiap.agendafit.dto;

import br.com.fiap.agendafit.model.Role;

public record UserResponse(Long id, String name, String email, Role role) {
}
