package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts demo vehicles at startup in the dev profile.
 * Idempotent: a vehicle whose registration number already exists is skipped.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final VehiculeRepository vehiculeRepository;

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
