package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.FlotteService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/console/vehicules")
public class VehiculeResource extends CrudResource<Vehicule, VehiculeResource.VehiculeDto> {

    public record VehiculeDto(Long id, String immatriculation, String marque, String modele,
                              CategorieVehicule categorie, BigDecimal tarifJournalier, StatutVehicule statut,
                              Long agenceId, String agenceNom,
                              List<Long> equipementIds, List<String> equipements) {
    }

    private final AgenceRepository agenceRepository;
    private final EquipementRepository equipementRepository;
    private final FlotteService flotteService;

    public VehiculeResource(VehiculeRepository repository, AgenceRepository agenceRepository,
                            EquipementRepository equipementRepository, FlotteService flotteService) {
        super(repository, "Véhicule");
        this.agenceRepository = agenceRepository;
        this.equipementRepository = equipementRepository;
        this.flotteService = flotteService;
    }

    static String label(Vehicule v) {
        return v.getMarque() + " " + v.getModele() + " · " + v.getImmatriculation();
    }

    @Override
    protected VehiculeDto toDto(Vehicule v) {
        Agence agence = v.getAgence();
        List<Equipement> equipements = v.getEquipements().stream()
                .sorted(Comparator.comparing(Equipement::getLibelle)).toList();
        return new VehiculeDto(v.getIdVehicule(), v.getImmatriculation(), v.getMarque(), v.getModele(),
                v.getCategorie(), v.getTarifJournalier(), v.getStatut(),
                agence == null ? null : agence.getIdAgence(), agence == null ? null : agence.getNom(),
                equipements.stream().map(Equipement::getIdEquipement).toList(),
                equipements.stream().map(Equipement::getLibelle).toList());
    }

    @Override
    protected Vehicule newEntity() {
        return new Vehicule();
    }

    @Override
    protected void apply(VehiculeDto dto, Vehicule v) {
        v.setImmatriculation(required(dto.immatriculation(), "immatriculation").toUpperCase());
        v.setMarque(required(dto.marque(), "marque"));
        v.setModele(required(dto.modele(), "modèle"));
        v.setCategorie(required(dto.categorie(), "catégorie"));
        v.setTarifJournalier(positive(dto.tarifJournalier(), "tarif journalier"));
        // The status is never typed by hand: it is computed from reservations and maintenances (afterWrite)
        if (v.getStatut() == null) {
            v.setStatut(StatutVehicule.DISPONIBLE);
        }
        v.setAgence(reference(agenceRepository, dto.agenceId(), "Agence"));

        // Vehicule owns the many-to-many: updating this Set rewrites the vehicule_equipement rows
        v.getEquipements().clear();
        if (dto.equipementIds() != null) {
            dto.equipementIds().forEach(id ->
                    v.getEquipements().add(reference(equipementRepository, id, "Équipement")));
        }
    }

    @Override
    protected void afterWrite(Vehicule v) {
        flotteService.synchroniser(v);
    }
}
