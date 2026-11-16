package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservations of the client logged in with this email, most recent first. */
    List<Reservation> findByClientEmailIgnoreCaseOrderByDateDebutDesc(String email);

    /** True if the vehicle already has a non-cancelled reservation overlapping [debut, fin]. */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicule.idVehicule = :vehiculeId
              and r.statut <> :exclu
              and r.dateDebut <= :fin and r.dateFin >= :debut""")
    boolean existsChevauchement(@Param("vehiculeId") Long vehiculeId, @Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin, @Param("exclu") StatutReservation exclu);
}
