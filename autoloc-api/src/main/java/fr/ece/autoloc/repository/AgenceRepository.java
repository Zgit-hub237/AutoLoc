package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Agence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgenceRepository extends JpaRepository<Agence, Long> {
}
