package br.com.fiap.agendafit.repository;

import br.com.fiap.agendafit.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
