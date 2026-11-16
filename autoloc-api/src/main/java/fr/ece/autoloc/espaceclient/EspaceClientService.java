package fr.ece.autoloc.espaceclient;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.InscriptionRequest;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.Location;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.LocationRequest;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.Profil;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.ProfilUpdate;
import fr.ece.autoloc.espaceclient.EspaceClientDtos.VehiculeOffre;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Business rules of the customer space: sign-up, availability, renting (reservation + contract + payment),
 * cancellation. Every method runs in a transaction, so LAZY associations can be read safely.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EspaceClientService {

    static final int MOT_DE_PASSE_MIN = 8;
    static final int DUREE_MAX_JOURS = 60;

    private final ClientRepository clientRepository;
    private final VehiculeRepository vehiculeRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ Account

    public Profil inscrire(InscriptionRequest request) {
        String email = required(request.email(), "email").toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw badRequest("Adresse email invalide");
        }
        String motDePasse = request.motDePasse() == null ? "" : request.motDePasse();
        if (motDePasse.length() < MOT_DE_PASSE_MIN) {
            throw badRequest("Le mot de passe doit contenir au moins " + MOT_DE_PASSE_MIN + " caractères");
        }
        String numPermis = required(request.numPermis(), "numéro de permis").toUpperCase();
        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }
        if (clientRepository.existsByNumPermisIgnoreCase(numPermis)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce numéro de permis est déjà enregistré");
        }

        Client client = new Client();
        client.setPrenom(required(request.prenom(), "prénom"));
        client.setNom(required(request.nom(), "nom"));
        client.setEmail(email);
        client.setTelephone(optional(request.telephone()));
        client.setNumPermis(numPermis);
        client.setDateInscription(LocalDate.now());
        client.setMotDePasse(passwordEncoder.encode(motDePasse));   // only the BCrypt hash is stored
        return toProfil(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public Profil profil(String email) {
        return toProfil(client(email));
    }

    public Profil modifierProfil(String email, ProfilUpdate update) {
        Client client = client(email);
        client.setPrenom(required(update.prenom(), "prénom"));
        client.setNom(required(update.nom(), "nom"));
        client.setTelephone(optional(update.telephone()));
        return toProfil(client);   // managed entity: changes are flushed at commit (dirty checking)
    }

    // ------------------------------------------------------------------ Catalogue

    /** Vehicles that can be rented for the period (not in maintenance, no overlapping reservation). */
    @Transactional(readOnly = true)
    public List<VehiculeOffre> vehiculesDisponibles(LocalDate debut, LocalDate fin, CategorieVehicule categorie) {
        boolean periode = debut != null && fin != null;
        if (periode) {
            verifierPeriode(debut, fin);
        }
        Long nbJours = periode ? nbJours(debut, fin) : null;
        return vehiculeRepository.findAll().stream()
                .filter(v -> v.getStatut() != StatutVehicule.MAINTENANCE)
                .filter(v -> categorie == null || v.getCategorie() == categorie)
                .filter(v -> !periode || !reservationRepository.existsChevauchement(
                        v.getIdVehicule(), debut, fin, StatutReservation.ANNULEE))
                .sorted(Comparator.comparing(Vehicule::getTarifJournalier))
                .map(v -> new VehiculeOffre(v.getIdVehicule(), v.getMarque(), v.getModele(), v.getImmatriculation(),
                        v.getCategorie(),
                        v.getAgence() == null ? null : v.getAgence().getNom(),
                        v.getAgence() == null ? null : v.getAgence().getVille(),
                        v.getEquipements().stream().map(Equipement::getLibelle).sorted().toList(),
                        v.getTarifJournalier(), nbJours,
                        periode ? v.getTarifJournalier().multiply(BigDecimal.valueOf(nbJours)) : null))
                .toList();
    }

    // ------------------------------------------------------------------ Renting

    /**
     * Rents a vehicle: one reservation (CONFIRMEE), its contract and the payment of the full amount.
     * A single save(reservation) persists the three entities thanks to the cascades
     * Reservation -> Contrat (ALL) and Contrat -> Paiement (ALL).
     */
    public Location louer(String email, LocationRequest request) {
        Client client = client(email);
        LocalDate debut = required(request.dateDebut(), "date de début");
        LocalDate fin = required(request.dateFin(), "date de fin");
        verifierPeriode(debut, fin);
        if (request.modePaiement() == null) {
            throw badRequest("Choisissez un mode de paiement");
        }
        Vehicule vehicule = vehiculeRepository.findById(required(request.vehiculeId(), "véhicule"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));
        if (vehicule.getStatut() == StatutVehicule.MAINTENANCE
                || reservationRepository.existsChevauchement(vehicule.getIdVehicule(), debut, fin, StatutReservation.ANNULEE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce véhicule n'est plus disponible sur ces dates");
        }

        BigDecimal montant = vehicule.getTarifJournalier().multiply(BigDecimal.valueOf(nbJours(debut, fin)));

        Reservation reservation = new Reservation();
        reservation.setClient(client);
        reservation.setVehicule(vehicule);
        reservation.setDateDebut(debut);
        reservation.setDateFin(fin);
        reservation.setStatut(StatutReservation.CONFIRMEE);

        Contrat contrat = new Contrat();
        contrat.setDateSignature(LocalDate.now());
        contrat.setMontantTotal(montant);
        contrat.setValide(true);
        contrat.setReservation(reservation);     // owning side (foreign key)
        reservation.setContrat(contrat);         // inverse side, carries the cascade

        Paiement paiement = new Paiement();
        paiement.setMontant(montant);
        paiement.setDatePaiement(LocalDate.now());
        paiement.setModePaiement(request.modePaiement());
        contrat.ajouterPaiement(paiement);       // keeps both sides of Contrat <-> Paiement in sync

        return toLocation(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<Location> mesLocations(String email) {
        return reservationRepository.findByClientEmailIgnoreCaseOrderByDateDebutDesc(email).stream()
                .map(this::toLocation).toList();
    }

    /** Cancels a rental that has not started yet; the contract is kept but marked invalid. */
    public Location annuler(String email, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> r.getClient() != null && r.getClient().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location introuvable"));
        if (!annulable(reservation)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une location qui n'a pas encore commencé peut être annulée");
        }
        reservation.setStatut(StatutReservation.ANNULEE);
        if (reservation.getContrat() != null) {
            reservation.getContrat().setValide(false);
        }
        return toLocation(reservation);
    }

    // ------------------------------------------------------------------ Helpers

    private Client client(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte client introuvable"));
    }

    private static void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (debut.isBefore(LocalDate.now())) {
            throw badRequest("La date de début ne peut pas être dans le passé");
        }
        if (fin.isBefore(debut)) {
            throw badRequest("La date de fin précède la date de début");
        }
        if (nbJours(debut, fin) > DUREE_MAX_JOURS) {
            throw badRequest("Durée maximale en ligne : " + DUREE_MAX_JOURS + " jours");
        }
    }

    /** Inclusive count: from the 1st to the 3rd = 3 days. */
    static long nbJours(LocalDate debut, LocalDate fin) {
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    private static boolean annulable(Reservation r) {
        return (r.getStatut() == StatutReservation.EN_ATTENTE || r.getStatut() == StatutReservation.CONFIRMEE)
                && r.getDateDebut().isAfter(LocalDate.now());
    }

    private Profil toProfil(Client c) {
        return new Profil(c.getPrenom(), c.getNom(), c.getEmail(), c.getTelephone(), c.getNumPermis(),
                c.getDateInscription(), c.getReservations().size());
    }

    private Location toLocation(Reservation r) {
        Vehicule v = r.getVehicule();
        Contrat c = r.getContrat();
        List<Paiement> paiements = c == null ? List.of() : c.getPaiements();
        BigDecimal paye = paiements.stream().map(Paiement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Location(r.getIdReservation(),
                v.getMarque() + " " + v.getModele(), v.getImmatriculation(), v.getCategorie(),
                v.getAgence() == null ? null : v.getAgence().getNom(),
                r.getDateDebut(), r.getDateFin(), nbJours(r.getDateDebut(), r.getDateFin()), r.getStatut(),
                c == null ? null : c.getIdContrat(),
                c == null ? null : c.getMontantTotal(), paye,
                paiements.isEmpty() ? null : paiements.get(0).getModePaiement(),
                annulable(r));
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw badRequest("Le champ « " + field + " » est obligatoire");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest("Le champ « " + field + " » est obligatoire");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
