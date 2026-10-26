package br.com.fiap.agendafit.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import br.com.fiap.agendafit.model.Enrollment;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import br.com.fiap.agendafit.model.Role;
import br.com.fiap.agendafit.model.User;
import br.com.fiap.agendafit.repository.EnrollmentRepository;
import br.com.fiap.agendafit.repository.GymClassRepository;
import br.com.fiap.agendafit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private record Slot(LocalTime time, String label) {}

    private static final List<Slot> SLOTS = List.of(
            new Slot(LocalTime.of(7, 0), "at sunrise"),
            new Slot(LocalTime.of(9, 0), "morning"),
            new Slot(LocalTime.of(12, 0), "express"),
            new Slot(LocalTime.of(18, 0), "after work"),
            new Slot(LocalTime.of(19, 0), "intensive"),
            new Slot(LocalTime.of(20, 30), "relax")
    );

    private static final String[] INSTRUCTORS = {
            "Carla Mendes", "Rafael Souza", "Juliana Lima", "Marcos Oliveira", "Beatriz Rocha"
    };

    private static final int[] CAPACITIES = {20, 15, 12, 25, 10, 2};

    private final UserRepository userRepository;
    private final GymClassRepository gymClassRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        userRepository.save(user("Administrator", "admin@fit.com", "admin123", Role.ADMIN));
        User ana = userRepository.save(user("Ana Souza", "ana@fit.com", "ana123", Role.USER));
        User bruno = userRepository.save(user("Bruno Costa", "bruno@fit.com", "bruno123", Role.USER));

        LocalDate firstDay = LocalDate.now().plusDays(1);
        List<GymClass> classes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            int day = i / SLOTS.size();
            Slot slot = SLOTS.get(i % SLOTS.size());
            Modality modality = Modality.values()[(i * 2 + day) % Modality.values().length];

            classes.add(GymClass.builder()
                    .title(label(modality) + " " + slot.label())
                    .modality(modality)
                    .level(Level.values()[i % Level.values().length])
                    .instructor(INSTRUCTORS[(i + day) % INSTRUCTORS.length])
                    .startsAt(LocalDateTime.of(firstDay.plusDays(day), slot.time()))
                    .durationMinutes(duration(modality))
                    .capacity(CAPACITIES[(i + day) % CAPACITIES.length])
                    .build());
        }
        gymClassRepository.saveAll(classes);

        List<Enrollment> enrollments = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            GymClass gymClass = classes.get(i);
            if (gymClass.getCapacity() == 2) {
                enrollments.add(enrollment(ana, gymClass));
                enrollments.add(enrollment(bruno, gymClass));
            } else if (i % 7 == 0) {
                enrollments.add(enrollment(ana, gymClass));
            }
        }
        enrollmentRepository.saveAll(enrollments);
    }

    private User user(String name, String email, String password, Role role) {
        return User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build();
    }

    private Enrollment enrollment(User user, GymClass gymClass) {
        return Enrollment.builder()
                .user(user)
                .gymClass(gymClass)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String label(Modality modality) {
        return switch (modality) {
            case YOGA -> "Yoga";
            case PILATES -> "Pilates";
            case SPINNING -> "Spinning";
            case FUNCTIONAL -> "Functional";
            case DANCE -> "Dance";
        };
    }

    private int duration(Modality modality) {
        return switch (modality) {
            case YOGA, DANCE -> 60;
            case PILATES -> 50;
            case SPINNING, FUNCTIONAL -> 45;
        };
    }
}
