package fr.ece.autoloc.console;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.FlotteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/console/maintenances")
public class MaintenanceResource extends CrudResource<Maintenance, MaintenanceResource.MaintenanceDto> {

    public record MaintenanceDto(Long id, LocalDate dateDebut, LocalDate dateFin, String description,
                                 Long vehiculeId, String vehiculeLabel) {
    }

    private final VehiculeRepository vehiculeRepository;
    private final FlotteService flotteService;

    public MaintenanceResource(MaintenanceRepository repository, VehiculeRepository vehiculeRepository,
                               FlotteService flotteService) {
        super(repository, "Maintenance");
        this.vehiculeRepository = vehiculeRepository;
        this.flotteService = flotteService;
    }

    @Override
    protected MaintenanceDto toDto(Maintenance m) {
        Vehicule v = m.getVehicule();
        return new MaintenanceDto(m.getIdMaintenance(), m.getDateDebut(), m.getDateFin(), m.getDescription(),
                v == null ? null : v.getIdVehicule(), v == null ? null : VehiculeResource.label(v));
    }

    @Override
    protected Maintenance newEntity() {
        return new Maintenance();
    }

    @Override
    protected void apply(MaintenanceDto dto, Maintenance m) {
        LocalDate debut = required(dto.dateDebut(), "date de début");
        if (dto.dateFin() != null && dto.dateFin().isBefore(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début");
        }
        m.setDateDebut(debut);
        m.setDateFin(dto.dateFin());
        m.setDescription(required(dto.description(), "description"));
        m.setVehicule(reference(vehiculeRepository, required(dto.vehiculeId(), "véhicule"), "Véhicule"));
    }

    /** A maintenance in progress puts the vehicle in MAINTENANCE; its end makes it available again. */
    @Override
    protected void afterWrite(Maintenance m) {
        flotteService.synchroniserTout();   // the vehicle may also have been changed
    }

    @Override
    protected void afterDelete(Maintenance m) {
        flotteService.synchroniserTout();
    }
}
