package br.com.fiap.agendafit.service;

import java.time.LocalDateTime;
import java.util.Set;

import br.com.fiap.agendafit.dto.EnrollmentResponse;
import br.com.fiap.agendafit.model.Enrollment;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.model.User;
import br.com.fiap.agendafit.repository.EnrollmentRepository;
import br.com.fiap.agendafit.repository.GymClassRepository;
import br.com.fiap.agendafit.repository.UserRepository;
import br.com.fiap.agendafit.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository repository;
    private final GymClassRepository classRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @Transactional
    public EnrollmentView enroll(Long classId) {
        GymClass gymClass = classRepository.findLockedById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        User user = userRepository.findByEmail(email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (repository.existsByUserIdAndGymClassId(user.getId(), classId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already enrolled in this class");
        }
        if (repository.countByGymClassId(classId) >= gymClass.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Class is full");
        }
        Enrollment enrollment = repository.saveAndFlush(Enrollment.builder().user(user).gymClass(gymClass)
                .createdAt(LocalDateTime.now()).build());
        return view(enrollment);
    }

    public Page<EnrollmentView> findMine(Pageable pageable) {
        Set<String> allowed = Set.of("id", "createdAt", "gymClass.startsAt");
        if (pageable.getSort().stream().anyMatch(order -> !allowed.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort property");
        }
        return repository.findByUserEmail(email(), pageable).map(this::view);
    }

    @Transactional
    public void cancel(Long id) {
        Enrollment enrollment = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        if (!currentUser.isAdmin() && !enrollment.getUser().getEmail().equals(email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        classRepository.findLockedById(enrollment.getGymClass().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
        }
        repository.delete(enrollment);
    }

    private String email() {
        return currentUser.email()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private EnrollmentView view(Enrollment enrollment) {
        GymClass gymClass = enrollment.getGymClass();
        return new EnrollmentView(new EnrollmentResponse(enrollment.getId(), gymClass.getId(), gymClass.getTitle(),
                gymClass.getStartsAt(), enrollment.getCreatedAt()), enrollment.getUser().getEmail());
    }
}
