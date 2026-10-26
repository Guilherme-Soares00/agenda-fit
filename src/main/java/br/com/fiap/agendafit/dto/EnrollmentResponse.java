package br.com.fiap.agendafit.dto;

import java.time.LocalDateTime;

import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "enrollments", itemRelation = "enrollment")
public record EnrollmentResponse(Long id, Long classId, String classTitle, LocalDateTime startsAt,
        LocalDateTime createdAt) {
}
