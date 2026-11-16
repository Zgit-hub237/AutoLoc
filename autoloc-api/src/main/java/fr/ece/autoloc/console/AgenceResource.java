package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.repository.AgenceRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/agences")
public class AgenceResource extends CrudResource<Agence, AgenceResource.AgenceDto> {

    public record AgenceDto(Long id, String nom, String ville, String adresse, String telephone,
                            Integer nbVehicules, Integer nbEmployes) {
    }

    public AgenceResource(AgenceRepository repository) {
        super(repository, "Agence");
    }

    @Override
    protected AgenceDto toDto(Agence a) {
        return new AgenceDto(a.getIdAgence(), a.getNom(), a.getVille(), a.getAdresse(), a.getTelephone(),
                a.getVehicules().size(), a.getEmployes().size());
    }

    @Override
    protected Agence newEntity() {
        return new Agence();
    }

    @Override
    protected void apply(AgenceDto dto, Agence a) {
        a.setNom(required(dto.nom(), "nom"));
        a.setVille(required(dto.ville(), "ville"));
        a.setAdresse(required(dto.adresse(), "adresse"));
        a.setTelephone(optional(dto.telephone()));
    }
}
