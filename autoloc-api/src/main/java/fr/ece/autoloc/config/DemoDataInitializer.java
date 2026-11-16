package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inserts demo data at startup in the dev profile: three vehicles, one customer account and two employee
 * accounts (one MANAGER, one AGENT) for the agency console. Idempotent: existing rows are skipped.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    static final String DEMO_CLIENT_EMAIL = "camille.martin@example.com";

    private final VehiculeRepository vehiculeRepository;
    private final ClientRepository clientRepository;
    private final EmployeRepository employeRepository;
    private final AgenceRepository agenceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        List<Vehicule> vehiculesDemo = List.of(
                vehicule("GH-111-AA", "Toyota", "Yaris", CategorieVehicule.CITADINE, "39.90"),
                vehicule("GH-222-BB", "Tesla", "Model 3", CategorieVehicule.BERLINE, "119.00"),
                vehicule("GH-333-CC", "Dacia", "Duster", CategorieVehicule.SUV, "59.50"));

        vehiculesDemo.stream()
                .filter(v -> !vehiculeRepository.existsByImmatriculation(v.getImmatriculation()))
                .forEach(v -> {
                    vehiculeRepository.save(v);
                    log.info("Demo vehicle created: {} {} ({})", v.getMarque(), v.getModele(), v.getImmatriculation());
                });

        if (!clientRepository.existsByEmailIgnoreCase(DEMO_CLIENT_EMAIL)) {
            Client client = new Client();
            client.setPrenom("Camille");
            client.setNom("Martin");
            client.setEmail(DEMO_CLIENT_EMAIL);
            client.setTelephone("0601020304");
            client.setNumPermis("DEMO-CLIENT-01");
            client.setDateInscription(LocalDate.now());
            client.setMotDePasse(passwordEncoder.encode("client123"));   // local demo account only
            clientRepository.save(client);
            log.info("Demo customer account created: {}", DEMO_CLIENT_EMAIL);
        }

        employe("Julie", "Moreau", RoleEmploye.MANAGER, "manager", "manager123");
        employe("Hugo", "Petit", RoleEmploye.AGENT, "agent", "agent123");
    }

    /** Local demo employee account; the rights come from the role (see SecurityConfig). */
    private void employe(String prenom, String nom, RoleEmploye role, String login, String motDePasse) {
        if (employeRepository.findByLoginIgnoreCase(login).isPresent()) {
            return;
        }
        Employe employe = new Employe();
        employe.setPrenom(prenom);
        employe.setNom(nom);
        employe.setRole(role);
        employe.setAgence(agenceRepository.findAll().stream().findFirst().orElse(null));
        employe.setLogin(login);
        employe.setMotDePasse(passwordEncoder.encode(motDePasse));   // local demo account only
        employeRepository.save(employe);
        log.info("Demo employee account created: {} ({})", login, role);
    }

    private Vehicule vehicule(String immatriculation, String marque, String modele,
                              CategorieVehicule categorie, String tarifJournalier) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque(marque);
        vehicule.setModele(modele);
        vehicule.setCategorie(categorie);
        vehicule.setTarifJournalier(new BigDecimal(tarifJournalier));
        vehicule.setStatut(StatutVehicule.DISPONIBLE);
        return vehicule;
    }
}
