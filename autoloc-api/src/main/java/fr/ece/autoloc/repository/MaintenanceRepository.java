package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    /** True if a maintenance of the vehicle overlaps [debut, fin]; no end date means "still in progress". */
    @Query("""
            select count(m) > 0 from Maintenance m
            where m.vehicule.idVehicule = :vehiculeId
              and m.dateDebut <= :fin
              and (m.dateFin is null or m.dateFin >= :debut)""")
    boolean existsChevauchement(@Param("vehiculeId") Long vehiculeId, @Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin);
}
