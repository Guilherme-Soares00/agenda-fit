package br.com.fiap.agendafit.service;

import java.util.List;

import br.com.fiap.agendafit.dto.GymClassRequest;
import br.com.fiap.agendafit.model.GymClass;
import br.com.fiap.agendafit.repository.EnrollmentRepository;
import br.com.fiap.agendafit.repository.GymClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GymClassService {

    private final GymClassRepository repository;
    private final EnrollmentRepository enrollmentRepository;

    public List<GymClass> findAll() {
        return repository.findAll(Sort.by("startsAt"));
    }

    public GymClass findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    public GymClass create(GymClassRequest request) {
        return repository.save(request.toEntity());
    }

    public GymClass update(Long id, GymClassRequest request) {
        findById(id);
        GymClass gymClass = request.toEntity();
        gymClass.setId(id);
        return repository.save(gymClass);
    }

    // apaga as inscrições antes, senão a chave estrangeira impede o delete
    @Transactional
    public void delete(Long id) {
        GymClass gymClass = findById(id);
        enrollmentRepository.deleteByGymClassId(id);
        repository.delete(gymClass);
    }
}
