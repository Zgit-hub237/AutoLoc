package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Session, reference data and dashboard figures for the web console. */
@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final EmployeRepository employeRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final ContratRepository contratRepository;
    private final PaiementRepository paiementRepository;
    private final MaintenanceRepository maintenanceRepository;

    public record Me(String username, List<String> roles, boolean admin) {
    }

    public record UpcomingReservation(Long id, LocalDate dateDebut, LocalDate dateFin, StatutReservation statut,
                                      String client, String vehicule) {
    }

    public record OpenMaintenance(Long id, LocalDate dateDebut, String description, String vehicule) {
    }

    public record Stats(Map<String, Long> totaux,
                        Map<StatutVehicule, Long> vehiculesParStatut,
                        Map<StatutReservation, Long> reservationsParStatut,
                        BigDecimal montantEncaisse,
                        List<UpcomingReservation> prochainesReservations,
                        List<OpenMaintenance> maintenancesEnCours) {
    }

    /** Identity of the authenticated user: used by the console to log in and adapt the screens. */
    @GetMapping("/me")
    public Me me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))          // Spring Security 7 also adds FACTOR_* authorities
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return new Me(authentication.getName(), roles, roles.contains("ADMIN"));
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

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        Map<String, Long> totaux = new LinkedHashMap<>();
        totaux.put("vehicules", vehiculeRepository.count());
        totaux.put("agences", agenceRepository.count());
        totaux.put("employes", employeRepository.count());
        totaux.put("clients", clientRepository.count());
        totaux.put("reservations", reservationRepository.count());
        totaux.put("contrats", contratRepository.count());

        List<Reservation> reservations = reservationRepository.findAll();
        LocalDate today = LocalDate.now();

        List<UpcomingReservation> prochaines = reservations.stream()
                .filter(r -> !r.getDateFin().isBefore(today) && r.getStatut() != StatutReservation.ANNULEE)
                .sorted(Comparator.comparing(Reservation::getDateDebut))
                .limit(5)
                .map(r -> new UpcomingReservation(r.getIdReservation(), r.getDateDebut(), r.getDateFin(),
                        r.getStatut(),
                        r.getClient() == null ? "-" : ClientResource.label(r.getClient()),
                        r.getVehicule() == null ? "-" : VehiculeResource.label(r.getVehicule())))
                .toList();

        List<OpenMaintenance> enCours = maintenanceRepository.findAll().stream()
                .filter(m -> m.getDateFin() == null || !m.getDateFin().isBefore(today))
                .sorted(Comparator.comparing(Maintenance::getDateDebut))
                .map(m -> new OpenMaintenance(m.getIdMaintenance(), m.getDateDebut(), m.getDescription(),
                        m.getVehicule() == null ? "-" : VehiculeResource.label(m.getVehicule())))
                .toList();

        BigDecimal encaisse = paiementRepository.findAll().stream()
                .map(Paiement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Stats(totaux,
                countBy(vehiculeRepository.findAll(), Vehicule::getStatut, StatutVehicule.class),
                countBy(reservations, Reservation::getStatut, StatutReservation.class),
                encaisse, prochaines, enCours);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /** Counts per enum value, including the values that have no row (0). */
    private static <T, K extends Enum<K>> Map<K, Long> countBy(List<T> items, Function<T, K> key, Class<K> type) {
        Map<K, Long> counts = new EnumMap<>(type);
        for (K k : type.getEnumConstants()) {
            counts.put(k, 0L);
        }
        counts.putAll(items.stream().filter(i -> key.apply(i) != null)
                .collect(Collectors.groupingBy(key, Collectors.counting())));
        return counts;
    }
}
