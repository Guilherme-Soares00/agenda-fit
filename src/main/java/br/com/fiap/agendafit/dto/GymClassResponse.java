package br.com.fiap.agendafit.dto;

import java.time.LocalDateTime;

import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "classes", itemRelation = "class")
public record GymClassResponse(Long id, String title, Modality modality, Level level,
        String instructor, LocalDateTime startsAt, Integer durationMinutes,
        Integer capacity, long availableSpots) {
}
