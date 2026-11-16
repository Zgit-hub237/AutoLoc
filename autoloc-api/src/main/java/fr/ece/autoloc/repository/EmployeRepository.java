package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeRepository extends JpaRepository<Employe, Long> {
}
