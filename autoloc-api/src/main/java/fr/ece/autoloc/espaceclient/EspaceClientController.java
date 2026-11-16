package fr.ece.autoloc.espaceclient;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.InscriptionRequest;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.Location;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.LocationRequest;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.Profil;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.ProfilUpdate;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.VehiculeOffre;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Customer space. The logged-in customer is always taken from the authentication (email),
 * never from the request: a customer can only see and modify their own data.
 */
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class EspaceClientController {

    private final EspaceClientService service;

    /** Open to anonymous visitors (see SecurityConfig). */
    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public Profil inscrire(@RequestBody InscriptionRequest request) {
        return service.inscrire(request);
    }

    @GetMapping("/profil")
    public Profil profil(Authentication auth) {
        return service.profil(auth.getName());
    }

    @PutMapping("/profil")
    public Profil modifierProfil(Authentication auth, @RequestBody ProfilUpdate update) {
        return service.modifierProfil(auth.getName(), update);
    }

    @GetMapping("/vehicules")
    public List<VehiculeOffre> vehicules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) CategorieVehicule categorie) {
        return service.vehiculesDisponibles(debut, fin, categorie);
    }

    @GetMapping("/locations")
    public List<Location> mesLocations(Authentication auth) {
        return service.mesLocations(auth.getName());
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public Location louer(Authentication auth, @RequestBody LocationRequest request) {
        return service.louer(auth.getName(), request);
    }

    @PostMapping("/locations/{id}/annulation")
    public Location annuler(Authentication auth, @PathVariable Long id) {
        return service.annuler(auth.getName(), id);
    }
}
