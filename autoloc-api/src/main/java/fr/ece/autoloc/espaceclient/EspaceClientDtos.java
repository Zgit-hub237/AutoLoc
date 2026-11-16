package fr.ece.autoloc.espaceclient;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.StatutReservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Data exchanged with the customer space (never the JPA entities themselves). */
public final class EspaceClientDtos {

    private EspaceClientDtos() {
    }

    public record InscriptionRequest(String prenom, String nom, String email, String telephone,
                                     String numPermis, String motDePasse) {
    }

    public record ProfilUpdate(String prenom, String nom, String telephone) {
    }

    public record Profil(String prenom, String nom, String email, String telephone, String numPermis,
                         LocalDate dateInscription, int nbLocations) {
    }

    /** A vehicle offered for the requested period, with its price. */
    public record VehiculeOffre(Long id, String marque, String modele, String immatriculation,
                                CategorieVehicule categorie, String agence, String ville,
                                List<String> equipements, BigDecimal tarifJournalier,
                                Long nbJours, BigDecimal total) {
    }

    public record LocationRequest(Long vehiculeId, LocalDate dateDebut, LocalDate dateFin,
                                  ModePaiement modePaiement) {
    }

    public record Location(Long id, String vehicule, String immatriculation, CategorieVehicule categorie,
                           String agence, LocalDate dateDebut, LocalDate dateFin, long nbJours,
                           StatutReservation statut, Long contratId, BigDecimal montantTotal,
                           BigDecimal montantPaye, ModePaiement modePaiement, boolean annulable) {
    }
}
