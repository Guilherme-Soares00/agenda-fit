package br.com.fiap.agendafit.service;

import br.com.fiap.agendafit.dto.GymClassResponse;

public record GymClassView(GymClassResponse response, boolean enrolled) {
}
