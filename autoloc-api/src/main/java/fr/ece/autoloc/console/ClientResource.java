package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Customers managed by the agency. A customer registered at the counter can be given an online access
 * (password) so they can log in to the customer space with their email.
 */
@RestController
@RequestMapping("/api/console/clients")
public class ClientResource extends CrudResource<Client, ClientResource.ClientDto> {

    /** motDePasse is write-only: sent by the form, never returned. */
    public record ClientDto(Long id, String nom, String prenom, String email, String telephone,
                            String numPermis, LocalDate dateInscription, Integer nbReservations,
                            String motDePasse, Boolean accesEnLigne) {
    }

    private final PasswordEncoder passwordEncoder;

    public ClientResource(ClientRepository repository, PasswordEncoder passwordEncoder) {
        super(repository, "Client");
        this.passwordEncoder = passwordEncoder;
    }

    static String label(Client c) {
        return c.getPrenom() + " " + c.getNom();
    }

    @Override
    protected ClientDto toDto(Client c) {
        return new ClientDto(c.getIdClient(), c.getNom(), c.getPrenom(), c.getEmail(), c.getTelephone(),
                c.getNumPermis(), c.getDateInscription(), c.getReservations().size(),
                null, c.getMotDePasse() != null);
    }

    @Override
    protected Client newEntity() {
        return new Client();
    }

    @Override
    protected void apply(ClientDto dto, Client c) {
        c.setNom(required(dto.nom(), "nom"));
        c.setPrenom(required(dto.prenom(), "prénom"));
        String email = required(dto.email(), "email").toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse email invalide");
        }
        c.setEmail(email);
        c.setTelephone(optional(dto.telephone()));
        c.setNumPermis(required(dto.numPermis(), "numéro de permis").toUpperCase());
        if (c.getDateInscription() == null) {
            c.setDateInscription(dto.dateInscription() == null ? LocalDate.now() : dto.dateInscription());
        }
        // Optional: open or reset the online access. Empty = unchanged.
        if (dto.motDePasse() != null && !dto.motDePasse().isEmpty()) {
            if (dto.motDePasse().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le mot de passe doit contenir au moins 8 caractères");
            }
            c.setMotDePasse(passwordEncoder.encode(dto.motDePasse()));
        }
    }
}
