package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Employees and their console account. The administrator chooses the login and the password;
 * the role of the employee (AGENT or MANAGER) gives the rights in the console (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/console/employes")
public class EmployeResource extends CrudResource<Employe, EmployeResource.EmployeDto> {

    /** motDePasse is write-only: sent by the form, never returned (always null in responses). */
    public record EmployeDto(Long id, String nom, String prenom, RoleEmploye role,
                             Long agenceId, String agenceNom,
                             String login, String motDePasse, Boolean compteActif) {
    }

    static final int MOT_DE_PASSE_MIN = 8;
    /** Accounts defined in memory (SecurityConfig): an employee cannot take these logins. */
    static final Set<String> LOGINS_RESERVES = Set.of("admin", "student");

    private final EmployeRepository employeRepository;
    private final AgenceRepository agenceRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeResource(EmployeRepository repository, AgenceRepository agenceRepository,
                           PasswordEncoder passwordEncoder) {
        super(repository, "Employé");
        this.employeRepository = repository;
        this.agenceRepository = agenceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected EmployeDto toDto(Employe e) {
        Agence agence = e.getAgence();
        return new EmployeDto(e.getIdEmploye(), e.getNom(), e.getPrenom(), e.getRole(),
                agence == null ? null : agence.getIdAgence(), agence == null ? null : agence.getNom(),
                e.getLogin(), null, e.getLogin() != null && e.getMotDePasse() != null);
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

        // Console account: optional. Empty login = no access (and the password is removed).
        String login = optional(dto.login());
        String motDePasse = dto.motDePasse() == null || dto.motDePasse().isEmpty() ? null : dto.motDePasse();
        if (login == null) {
            e.setLogin(null);
            e.setMotDePasse(null);
            return;
        }
        login = login.toLowerCase();
        if (!login.matches("^[a-z0-9._-]{3,30}$")) {
            throw badRequest("Identifiant invalide : 3 à 30 caractères parmi a-z, 0-9, point, tiret, souligné");
        }
        long id = e.getIdEmploye() == null ? 0L : e.getIdEmploye();
        if (LOGINS_RESERVES.contains(login) || employeRepository.existsByLoginIgnoreCaseAndIdEmployeNot(login, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant est déjà utilisé");
        }
        if (motDePasse == null && e.getMotDePasse() == null) {
            throw badRequest("Définissez un mot de passe pour ouvrir le compte de l'employé");
        }
        if (motDePasse != null && motDePasse.length() < MOT_DE_PASSE_MIN) {
            throw badRequest("Le mot de passe doit contenir au moins " + MOT_DE_PASSE_MIN + " caractères");
        }
        e.setLogin(login);
        if (motDePasse != null) {
            e.setMotDePasse(passwordEncoder.encode(motDePasse));   // empty on update = unchanged
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
