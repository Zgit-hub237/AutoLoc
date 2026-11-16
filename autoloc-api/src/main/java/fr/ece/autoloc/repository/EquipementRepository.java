package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Equipement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipementRepository extends JpaRepository<Equipement, Long> {
}
