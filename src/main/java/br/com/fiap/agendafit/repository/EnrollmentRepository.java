package br.com.fiap.agendafit.repository;

import br.com.fiap.agendafit.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    void deleteByGymClassId(Long gymClassId);
}
