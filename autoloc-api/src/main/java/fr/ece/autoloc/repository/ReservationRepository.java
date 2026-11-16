package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
