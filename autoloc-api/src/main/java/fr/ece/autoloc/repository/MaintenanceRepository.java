package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
}
