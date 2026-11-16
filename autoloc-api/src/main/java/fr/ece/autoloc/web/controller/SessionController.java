package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Information shared by every authenticated role: who is logged in, and the domain enum values. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

    private final EmployeRepository employeRepository;
    private final ClientRepository clientRepository;

    /** nomAffiche: first and last name for employees and customers, the login otherwise. */
    public record Me(String username, List<String> roles, String nomAffiche) {
    }

    /** Used by the web interface to log in and choose the agency console or the customer space. */
    @GetMapping("/me")
    public Me me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))          // Spring Security 7 also adds FACTOR_* authorities
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        String login = authentication.getName();
        String nom = employeRepository.findByLoginIgnoreCase(login).map(e -> e.getPrenom() + " " + e.getNom())
                .or(() -> clientRepository.findByEmailIgnoreCase(login).map(c -> c.getPrenom() + " " + c.getNom()))
                .orElse(login);
        return new Me(login, roles, nom);
    }

    /** Enum values, so the forms always offer exactly what the domain accepts. */
    @GetMapping("/enums")
    public Map<String, List<String>> enums() {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        enums.put("CategorieVehicule", names(CategorieVehicule.values()));
        enums.put("StatutVehicule", names(StatutVehicule.values()));
        enums.put("RoleEmploye", names(RoleEmploye.values()));
        enums.put("StatutReservation", names(StatutReservation.values()));
        enums.put("ModePaiement", names(ModePaiement.values()));
        return enums;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
