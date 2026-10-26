package br.com.fiap.agendafit.service;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import br.com.fiap.agendafit.dto.GymClassRequest;
import br.com.fiap.agendafit.dto.GymClassResponse;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.model.Level;
import br.com.fiap.agendafit.model.Modality;
import br.com.fiap.agendafit.repository.EnrollmentCount;
import br.com.fiap.agendafit.repository.EnrollmentRepository;
import br.com.fiap.agendafit.repository.GymClassRepository;
import br.com.fiap.agendafit.repository.GymClassSpecifications;
import br.com.fiap.agendafit.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymClassService {

    private final GymClassRepository repository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurrentUser currentUser;

    public Page<GymClassView> findAll(Pageable pageable) {
        validateSort(pageable);
        return responses(repository.findAll(pageable));
    }

    public Page<GymClassView> findByExample(Modality modality, Level level, String instructor, Pageable pageable) {
        validateSort(pageable);
        GymClass probe = GymClass.builder().modality(modality).level(level).instructor(instructor).build();
        ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreCase().withIgnoreNullValues()
                .withIgnorePaths("id", "capacity", "durationMinutes", "startsAt", "title");
        return responses(repository.findAll(Example.of(probe, matcher), pageable));
    }

    public Page<GymClassView> search(String title, Modality modality, Level level, LocalDateTime startsAfter,
            LocalDateTime startsBefore, Boolean availableOnly, Pageable pageable) {
        validateSort(pageable);
        return responses(repository.findAll(GymClassSpecifications.titleContains(title)
                .and(GymClassSpecifications.modalityEquals(modality))
                .and(GymClassSpecifications.levelEquals(level))
                .and(GymClassSpecifications.startsAfter(startsAfter))
                .and(GymClassSpecifications.startsBefore(startsBefore))
                .and(GymClassSpecifications.hasAvailability(availableOnly)), pageable));
    }

    private void validateSort(Pageable pageable) {
        Set<String> allowed = Set.of("id", "title", "modality", "level", "instructor", "startsAt", "durationMinutes", "capacity");
        if (pageable.getSort().stream().anyMatch(order -> !allowed.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort property");
        }
    }

    private Page<GymClassView> responses(Page<GymClass> page) {
        Map<Long, Long> counts = page.isEmpty() ? Map.of() : enrollmentRepository
                .countByClassIds(page.getContent().stream().map(GymClass::getId).toList()).stream()
                .collect(Collectors.toMap(EnrollmentCount::getClassId, EnrollmentCount::getTotal));
        Set<Long> enrolled = page.isEmpty() ? Set.of() : currentUser.email()
                .map(email -> enrollmentRepository.findEnrolledClassIds(email,
                        page.getContent().stream().map(GymClass::getId).toList())).orElseGet(Set::of);
        return page.map(gymClass -> new GymClassView(new GymClassResponse(gymClass.getId(), gymClass.getTitle(),
                gymClass.getModality(), gymClass.getLevel(), gymClass.getInstructor(), gymClass.getStartsAt(),
                gymClass.getDurationMinutes(), gymClass.getCapacity(),
                gymClass.getCapacity() - counts.getOrDefault(gymClass.getId(), 0L)), enrolled.contains(gymClass.getId())));
    }

    public GymClassView findById(Long id) {
        return responses(new PageImpl<>(List.of(requireClass(id)))).getContent().getFirst();
    }

    private GymClass requireClass(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    @Transactional
    public GymClassView create(GymClassRequest request) {
        GymClass gymClass = repository.save(request.toEntity());
        return responses(new PageImpl<>(List.of(gymClass))).getContent().getFirst();
    }

    @Transactional
    public GymClassView update(Long id, GymClassRequest request) {
        GymClass gymClass = lockedClass(id);
        if (request.capacity() < enrollmentRepository.countByGymClassId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Capacity cannot be lower than the number of enrollments");
        }
        gymClass.setTitle(request.title());
        gymClass.setModality(request.modality());
        gymClass.setLevel(request.level());
        gymClass.setInstructor(request.instructor());
        gymClass.setStartsAt(request.startsAt());
        gymClass.setDurationMinutes(request.durationMinutes());
        gymClass.setCapacity(request.capacity());
        return responses(new PageImpl<>(List.of(gymClass))).getContent().getFirst();
    }


    @Transactional
    public void delete(Long id) {
        GymClass gymClass = lockedClass(id);
        enrollmentRepository.deleteByGymClassId(id);
        repository.delete(gymClass);
    }

    private GymClass lockedClass(Long id) {
        return repository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }
}
