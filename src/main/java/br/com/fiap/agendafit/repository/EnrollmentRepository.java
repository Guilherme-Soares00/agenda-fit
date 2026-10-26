package br.com.fiap.agendafit.repository;

import br.com.fiap.agendafit.model.Enrollment;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Modifying
    @Query("delete from Enrollment e where e.gymClass.id = :gymClassId")
    void deleteByGymClassId(Long gymClassId);

    boolean existsByUserIdAndGymClassId(Long userId, Long gymClassId);

    long countByGymClassId(Long gymClassId);

    @EntityGraph(attributePaths = {"gymClass", "user"})
    Page<Enrollment> findByUserEmail(String email, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"gymClass", "user"})
    Optional<Enrollment> findById(Long id);

    @Query("select e.gymClass.id from Enrollment e where e.user.email = :email and e.gymClass.id in :classIds")
    Set<Long> findEnrolledClassIds(String email, Collection<Long> classIds);

    @Query("select e.gymClass.id as classId, count(e) as total from Enrollment e where e.gymClass.id in :classIds group by e.gymClass.id")
    List<EnrollmentCount> countByClassIds(Collection<Long> classIds);
}
