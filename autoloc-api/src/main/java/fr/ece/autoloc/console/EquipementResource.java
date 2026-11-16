package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.EquipementRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/console/equipements")
public class EquipementResource extends CrudResource<Equipement, EquipementResource.EquipementDto> {

    public record EquipementDto(Long id, String libelle, Integer nbVehicules) {
    }

    public EquipementResource(EquipementRepository repository) {
        super(repository, "Équipement");
    }

    @Override
    protected EquipementDto toDto(Equipement e) {
        return new EquipementDto(e.getIdEquipement(), e.getLibelle(), e.getVehicules().size());
    }

    @Override
    protected Equipement newEntity() {
        return new Equipement();
    }

    @Override
    protected void apply(EquipementDto dto, Equipement e) {
        e.setLibelle(required(dto.libelle(), "libellé"));
    }

    /** Equipement is the inverse side: the join rows belong to Vehicule.equipements. */
    @Override
    protected void beforeDelete(Equipement e) {
        for (Vehicule v : new ArrayList<>(e.getVehicules())) {
            v.getEquipements().remove(e);
        }
    }
}
