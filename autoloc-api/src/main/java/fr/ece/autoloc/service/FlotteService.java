package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Keeps the fleet consistent with the reservations and maintenances.
 * <p>
 * The status of a vehicle is <b>computed</b>, never typed by hand:
 * <ul>
 *   <li>MAINTENANCE: a maintenance covers today (no end date = still in progress);</li>
 *   <li>LOUE: a CONFIRMEE reservation covers today;</li>
 *   <li>RESERVE: a reservation (pending or confirmed) is booked for a later date;</li>
 *   <li>DISPONIBLE otherwise.</li>
 * </ul>
 * A rental booked for later makes the vehicle RESERVE immediately, then LOUE on the departure day
 * (daily task at 00:05 and at every start-up). A RESERVE vehicle can still be rented on other dates:
 * availability for a period is checked by overlap, not by this status.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FlotteService {

    private final VehiculeRepository vehiculeRepository;
    private final ReservationRepository reservationRepository;
    private final MaintenanceRepository maintenanceRepository;

    public StatutVehicule statutCalcule(Vehicule vehicule, LocalDate jour) {
        Long id = vehicule.getIdVehicule();
        if (maintenanceRepository.existsChevauchement(id, jour, jour)) {
            return StatutVehicule.MAINTENANCE;
        }
        if (reservationRepository.existsLocationEnCours(id, jour)) {
            return StatutVehicule.LOUE;
        }
        if (reservationRepository.existsReservationAVenir(id, jour)) {
            return StatutVehicule.RESERVE;
        }
        return StatutVehicule.DISPONIBLE;
    }

    /** Recomputes the status of one vehicle (after a rental, a cancellation, a maintenance...). */
    public void synchroniser(Vehicule vehicule) {
        StatutVehicule statut = statutCalcule(vehicule, LocalDate.now());
        if (vehicule.getStatut() != statut) {
            log.info("Vehicle {} : {} -> {}", vehicule.getImmatriculation(), vehicule.getStatut(), statut);
            vehicule.setStatut(statut);   // managed entity: written at commit (dirty checking)
        }
    }

    /**
     * Daily update: closes past reservations, then recomputes every vehicle status.
     * - CONFIRMEE whose last day is past  -> TERMINEE
     * - EN_ATTENTE whose first day is past -> ANNULEE (never confirmed in time)
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void miseAJourQuotidienne() {
        LocalDate today = LocalDate.now();
        for (Reservation r : reservationRepository.findByStatutAndDateFinBefore(StatutReservation.CONFIRMEE, today)) {
            r.setStatut(StatutReservation.TERMINEE);
        }
        for (Reservation r : reservationRepository.findByStatutAndDateDebutBefore(StatutReservation.EN_ATTENTE, today)) {
            r.setStatut(StatutReservation.ANNULEE);
        }
        synchroniserTout();
    }

    public void synchroniserTout() {
        vehiculeRepository.findAll().forEach(this::synchroniser);
    }

    /** Also run at start-up: the application may have been stopped when the day changed. */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        miseAJourQuotidienne();
    }
}
