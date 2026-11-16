package fr.ece.autoloc.espaceclient;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.VehiculeOffre;
import fr.ece.autoloc.repository.AgenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Public website: anyone can browse the available vehicles and the agencies without an account.
 * Logging in is only required to rent (see EspaceClientController).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class CatalogueController {

    private final EspaceClientService service;
    private final AgenceRepository agenceRepository;

    public record AgencePublique(String nom, String ville, String adresse, String telephone, int nbVehicules) {
    }

    @GetMapping("/vehicules")
    public List<VehiculeOffre> vehicules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) CategorieVehicule categorie) {
        return service.vehiculesDisponibles(debut, fin, categorie);
    }

    @GetMapping("/agences")
    @Transactional(readOnly = true)
    public List<AgencePublique> agences() {
        return agenceRepository.findAll().stream()
                .sorted(Comparator.comparing(a -> a.getVille() + a.getNom()))
                .map(a -> new AgencePublique(a.getNom(), a.getVille(), a.getAdresse(), a.getTelephone(),
                        a.getVehicules().size()))
                .toList();
    }
}
