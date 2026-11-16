package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
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

    public ReservationResource(ReservationRepository repository, ClientRepository clientRepository,
                               VehiculeRepository vehiculeRepository) {
        super(repository, "Réservation");
        this.clientRepository = clientRepository;
        this.vehiculeRepository = vehiculeRepository;
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
    }
}
