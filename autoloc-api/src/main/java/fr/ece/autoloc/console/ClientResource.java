package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/console/clients")
public class ClientResource extends CrudResource<Client, ClientResource.ClientDto> {

    public record ClientDto(Long id, String nom, String prenom, String email, String telephone,
                            String numPermis, LocalDate dateInscription, Integer nbReservations) {
    }

    public ClientResource(ClientRepository repository) {
        super(repository, "Client");
    }

    static String label(Client c) {
        return c.getPrenom() + " " + c.getNom();
    }

    @Override
    protected ClientDto toDto(Client c) {
        return new ClientDto(c.getIdClient(), c.getNom(), c.getPrenom(), c.getEmail(), c.getTelephone(),
                c.getNumPermis(), c.getDateInscription(), c.getReservations().size());
    }

    @Override
    protected Client newEntity() {
        return new Client();
    }

    @Override
    protected void apply(ClientDto dto, Client c) {
        c.setNom(required(dto.nom(), "nom"));
        c.setPrenom(required(dto.prenom(), "prénom"));
        c.setEmail(required(dto.email(), "email").toLowerCase());
        c.setTelephone(optional(dto.telephone()));
        c.setNumPermis(required(dto.numPermis(), "numéro de permis"));
        c.setDateInscription(dto.dateInscription() == null ? LocalDate.now() : dto.dateInscription());
    }
}
