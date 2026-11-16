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

    /**
     * True if the vehicle has another active reservation (not cancelled, not finished) overlapping [debut, fin].
     * {@code exclureId} ignores the reservation being modified (use 0 for a new one).
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicule.idVehicule = :vehiculeId
              and r.idReservation <> :exclureId
              and r.statut in (fr.ece.autoloc.domain.StatutReservation.EN_ATTENTE,
                               fr.ece.autoloc.domain.StatutReservation.CONFIRMEE)
              and r.dateDebut <= :fin and r.dateFin >= :debut""")
    boolean existsChevauchement(@Param("vehiculeId") Long vehiculeId, @Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin, @Param("exclureId") Long exclureId);

    /** True if a confirmed rental of the vehicle covers this day: the vehicle is out (LOUE). */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicule.idVehicule = :vehiculeId
              and r.statut = fr.ece.autoloc.domain.StatutReservation.CONFIRMEE
              and r.dateDebut <= :jour and r.dateFin >= :jour""")
    boolean existsLocationEnCours(@Param("vehiculeId") Long vehiculeId, @Param("jour") LocalDate jour);

    /** True if the vehicle has an active reservation (pending or confirmed) starting after this day. */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicule.idVehicule = :vehiculeId
              and r.statut in (fr.ece.autoloc.domain.StatutReservation.EN_ATTENTE,
                               fr.ece.autoloc.domain.StatutReservation.CONFIRMEE)
              and r.dateDebut > :jour""")
    boolean existsReservationAVenir(@Param("vehiculeId") Long vehiculeId, @Param("jour") LocalDate jour);

    /** Reservations in a given status whose last day is before the date (used by the daily update). */
    List<Reservation> findByStatutAndDateFinBefore(StatutReservation statut, LocalDate date);

    /** Reservations in a given status whose first day is before the date (used by the daily update). */
    List<Reservation> findByStatutAndDateDebutBefore(StatutReservation statut, LocalDate date);
}
