package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/console/contrats")
public class ContratResource extends CrudResource<Contrat, ContratResource.ContratDto> {

    public record ContratDto(Long id, LocalDate dateSignature, BigDecimal montantTotal, Boolean valide,
                             Long reservationId, String reservationLabel,
                             BigDecimal totalPaye, Integer nbPaiements) {
    }

    private final ReservationRepository reservationRepository;

    public ContratResource(ContratRepository repository, ReservationRepository reservationRepository) {
        super(repository, "Contrat");
        this.reservationRepository = reservationRepository;
    }

    static String label(Contrat c) {
        return "C-" + c.getIdContrat() + " · " + c.getMontantTotal() + " €";
    }

    @Override
    protected ContratDto toDto(Contrat c) {
        Reservation r = c.getReservation();
        BigDecimal totalPaye = c.getPaiements().stream()
                .map(Paiement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ContratDto(c.getIdContrat(), c.getDateSignature(), c.getMontantTotal(), c.isValide(),
                r == null ? null : r.getIdReservation(), r == null ? null : ReservationResource.label(r),
                totalPaye, c.getPaiements().size());
    }

    @Override
    protected Contrat newEntity() {
        return new Contrat();
    }

    @Override
    protected void apply(ContratDto dto, Contrat c) {
        c.setDateSignature(dto.dateSignature() == null ? LocalDate.now() : dto.dateSignature());
        c.setMontantTotal(required(dto.montantTotal(), "montant total"));
        c.setValide(Boolean.TRUE.equals(dto.valide()));
        Reservation reservation = reference(reservationRepository,
                required(dto.reservationId(), "réservation"), "Réservation");
        c.setReservation(reservation);          // owning side: writes contrat.reservation_id_reservation
        reservation.setContrat(c);              // inverse side kept consistent in memory
    }

    /** The reservation cascades ALL to its contract: unlink it, otherwise the flush would re-save it. */
    @Override
    protected void beforeDelete(Contrat c) {
        if (c.getReservation() != null) {
            c.getReservation().setContrat(null);
        }
    }
}
