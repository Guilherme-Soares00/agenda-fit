package br.com.fiap.agendafit.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(indexes = {
        @Index(name = "idx_class_starts_at", columnList = "starts_at"),
        @Index(name = "idx_class_modality", columnList = "modality")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private Modality modality;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_level")
    private Level level;

    private String instructor;

    private LocalDateTime startsAt;

    private Integer durationMinutes;

    private Integer capacity;
}
