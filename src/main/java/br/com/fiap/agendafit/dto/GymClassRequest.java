package br.com.fiap.agendafit.dto;

import java.time.LocalDateTime;

import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Corpo do POST e do PUT de aulas: a aula sem o id
public record GymClassRequest(
        @NotBlank(message = "title is required") @Size(max = 100, message = "title must have at most 100 characters") String title,
        @NotNull(message = "modality is required") Modality modality,
        @NotNull(message = "level is required") Level level,
        @NotBlank(message = "instructor is required") String instructor,
        @NotNull(message = "startsAt is required") LocalDateTime startsAt,
        @NotNull(message = "durationMinutes is required") @Min(value = 15, message = "durationMinutes must be at least 15") @Max(value = 180, message = "durationMinutes must be at most 180") Integer durationMinutes,
        @NotNull(message = "capacity is required") @Min(value = 1, message = "capacity must be at least 1") Integer capacity
) {

    public GymClass toEntity() {
        return GymClass.builder()
                .title(title)
                .modality(modality)
                .level(level)
                .instructor(instructor)
                .startsAt(startsAt)
                .durationMinutes(durationMinutes)
                .capacity(capacity)
                .build();
    }
}
