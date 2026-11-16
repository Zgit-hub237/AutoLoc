package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/employes")
public class EmployeResource extends CrudResource<Employe, EmployeResource.EmployeDto> {

    public record EmployeDto(Long id, String nom, String prenom, RoleEmploye role,
                             Long agenceId, String agenceNom) {
    }

    private final AgenceRepository agenceRepository;

    public EmployeResource(EmployeRepository repository, AgenceRepository agenceRepository) {
        super(repository, "Employé");
        this.agenceRepository = agenceRepository;
    }

    @Override
    protected EmployeDto toDto(Employe e) {
        Agence agence = e.getAgence();
        return new EmployeDto(e.getIdEmploye(), e.getNom(), e.getPrenom(), e.getRole(),
                agence == null ? null : agence.getIdAgence(), agence == null ? null : agence.getNom());
    }

    @Override
    protected Employe newEntity() {
        return new Employe();
    }

    @Override
    protected void apply(EmployeDto dto, Employe e) {
        e.setNom(required(dto.nom(), "nom"));
        e.setPrenom(required(dto.prenom(), "prénom"));
        e.setRole(required(dto.role(), "rôle"));
        e.setAgence(reference(agenceRepository, dto.agenceId(), "Agence"));
    }
}
