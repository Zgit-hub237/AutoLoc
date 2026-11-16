package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.FlotteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/** Deleting a reservation cascades (ALL) to its contract, then to the contract's payments. */
@RestController
@RequestMapping("/api/console/reservations")
public class ReservationResource extends CrudResource<Reservation, ReservationResource.ReservationDto> {

    public record ReservationDto(Long id, LocalDate dateDebut, LocalDate dateFin, StatutReservation statut,
                                 Long clientId, String clientNom, Long vehiculeId, String vehiculeLabel,
                                 Long contratId) {
    }

    private final ClientRepository clientRepository;
    private final VehiculeRepository vehiculeRepository;
    private final ReservationRepository reservationRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final FlotteService flotteService;

    public ReservationResource(ReservationRepository repository, ClientRepository clientRepository,
                               VehiculeRepository vehiculeRepository, MaintenanceRepository maintenanceRepository,
                               FlotteService flotteService) {
        super(repository, "Réservation");
        this.clientRepository = clientRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.reservationRepository = repository;
        this.maintenanceRepository = maintenanceRepository;
        this.flotteService = flotteService;
    }

    static String label(Reservation r) {
        String client = r.getClient() == null ? "sans client" : ClientResource.label(r.getClient());
        return "R-" + r.getIdReservation() + " · " + client + " · " + r.getDateDebut();
    }

    @Override
    protected ReservationDto toDto(Reservation r) {
        Client c = r.getClient();
        Vehicule v = r.getVehicule();
        Contrat contrat = r.getContrat();
        return new ReservationDto(r.getIdReservation(), r.getDateDebut(), r.getDateFin(), r.getStatut(),
                c == null ? null : c.getIdClient(), c == null ? null : ClientResource.label(c),
                v == null ? null : v.getIdVehicule(), v == null ? null : VehiculeResource.label(v),
                contrat == null ? null : contrat.getIdContrat());
    }

    @Override
    protected Reservation newEntity() {
        return new Reservation();
    }

    @Override
    protected void apply(ReservationDto dto, Reservation r) {
        LocalDate debut = required(dto.dateDebut(), "date de début");
        LocalDate fin = required(dto.dateFin(), "date de fin");
        if (fin.isBefore(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début");
        }
        r.setDateDebut(debut);
        r.setDateFin(fin);
        r.setStatut(dto.statut() == null ? StatutReservation.EN_ATTENTE : dto.statut());
        r.setClient(reference(clientRepository, required(dto.clientId(), "client"), "Client"));
        r.setVehicule(reference(vehiculeRepository, required(dto.vehiculeId(), "véhicule"), "Véhicule"));

        // Same rules as the customer space: an active reservation needs a free vehicle
        boolean active = r.getStatut() == StatutReservation.EN_ATTENTE || r.getStatut() == StatutReservation.CONFIRMEE;
        Long vehiculeId = r.getVehicule().getIdVehicule();
        long exclure = r.getIdReservation() == null ? 0L : r.getIdReservation();
        if (active && reservationRepository.existsChevauchement(vehiculeId, debut, fin, exclure)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce véhicule est déjà réservé sur une partie de ces dates");
        }
        if (active && maintenanceRepository.existsChevauchement(vehiculeId, debut, fin)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce véhicule est en maintenance sur une partie de ces dates");
        }
        // A cancelled reservation keeps its contract for the history, but the contract is no longer valid
        if (r.getStatut() == StatutReservation.ANNULEE && r.getContrat() != null) {
            r.getContrat().setValide(false);
        }
    }

    @Override
    protected void afterWrite(Reservation r) {
        flotteService.synchroniserTout();   // old and new vehicle if it was changed
    }

    @Override
    protected void afterDelete(Reservation r) {
        flotteService.synchroniserTout();
    }
}
