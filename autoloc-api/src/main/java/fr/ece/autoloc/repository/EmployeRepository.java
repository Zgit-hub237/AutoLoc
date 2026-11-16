package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    Optional<Employe> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCaseAndIdEmployeNot(String login, Long idEmploye);
}
