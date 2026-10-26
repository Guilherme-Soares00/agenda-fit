package br.com.fiap.agendafit.service;

import br.com.fiap.agendafit.dto.EnrollmentResponse;

public record EnrollmentView(EnrollmentResponse response, String ownerEmail) {
}
