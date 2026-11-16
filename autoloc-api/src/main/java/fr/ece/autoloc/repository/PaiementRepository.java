package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
}
