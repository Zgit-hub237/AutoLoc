package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratRepository extends JpaRepository<Contrat, Long> {
}
