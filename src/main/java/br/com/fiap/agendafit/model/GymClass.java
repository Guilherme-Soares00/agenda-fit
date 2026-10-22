package br.com.fiap.agendafit.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Uma aula da grade da academia
@Entity
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

    // "level" é palavra reservada em alguns bancos
    @Enumerated(EnumType.STRING)
    @Column(name = "class_level")
    private Level level;

    private String instructor;

    private LocalDateTime startsAt;

    // Integer (e não int) para o Query by Example ignorar quando não informado
    private Integer durationMinutes;

    private Integer capacity;
}
