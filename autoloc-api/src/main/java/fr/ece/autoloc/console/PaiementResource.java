package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/console/paiements")
public class PaiementResource extends CrudResource<Paiement, PaiementResource.PaiementDto> {

    public record PaiementDto(Long id, BigDecimal montant, LocalDate datePaiement, ModePaiement modePaiement,
                              Long contratId, String contratLabel) {
    }

    private final ContratRepository contratRepository;

    public PaiementResource(PaiementRepository repository, ContratRepository contratRepository) {
        super(repository, "Paiement");
        this.contratRepository = contratRepository;
    }

    @Override
    protected PaiementDto toDto(Paiement p) {
        Contrat c = p.getContrat();
        return new PaiementDto(p.getIdPaiement(), p.getMontant(), p.getDatePaiement(), p.getModePaiement(),
                c == null ? null : c.getIdContrat(), c == null ? null : ContratResource.label(c));
    }

    @Override
    protected Paiement newEntity() {
        return new Paiement();
    }

    @Override
    protected void apply(PaiementDto dto, Paiement p) {
        BigDecimal montant = positive(dto.montant(), "montant");
        p.setDatePaiement(dto.datePaiement() == null ? LocalDate.now() : dto.datePaiement());
        p.setModePaiement(required(dto.modePaiement(), "mode de paiement"));
        // Owning side only: going through the old contract's collection would trigger orphanRemoval
        Contrat contrat = reference(contratRepository, required(dto.contratId(), "contrat"), "Contrat");
        BigDecimal dejaPaye = contrat.getPaiements().stream()
                .filter(autre -> autre != p)
                .map(Paiement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dejaPaye.add(montant).compareTo(contrat.getMontantTotal()) > 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Le total payé dépasserait le montant du contrat (" + contrat.getMontantTotal() + " €, déjà payé : "
                            + dejaPaye + " €)");
        }
        p.setMontant(montant);
        p.setContrat(contrat);
    }

    /** Removing it from the contract's collection deletes it through orphanRemoval. */
    @Override
    protected void beforeDelete(Paiement p) {
        if (p.getContrat() != null) {
            p.getContrat().retirerPaiement(p);
        }
    }
}
