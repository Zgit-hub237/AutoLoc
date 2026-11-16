# Bonus : application web AutoLoc (espace client + console de l'agence)

Une interface web servie par l'application Spring Boot elle-même (HTML/CSS/JavaScript natifs, sans
framework ni Node). Une seule page de connexion ; l'écran affiché dépend du rôle du compte.

## Lancer

1. Démarrer MySQL dans XAMPP.
2. Lancer `AutolocApiApplication` dans IntelliJ (ou `./mvnw spring-boot:run`).
3. Ouvrir **http://localhost:8080**.

## Les trois rôles

| Rôle | Qui | Connexion | Ce qu'il peut faire |
|---|---|---|---|
| `CLIENT` | un client de l'agence | son **email** + mot de passe choisi à l'inscription (stocké haché BCrypt en base) | rechercher un véhicule disponible, louer et payer, consulter et annuler ses locations, modifier son profil |
| `ADMIN` | un gestionnaire de l'agence | identifiant interne | tout gérer dans la console : 9 entités, tableau de bord |
| `USER` | un collaborateur en consultation (stagiaire, comptable…) | identifiant interne | voir la console **sans rien modifier** |

Le compte `USER` (`student`) vient de l'atelier 2 : c'est lui qui démontre le **403** (« authentifié mais
pas autorisé »). Dans l'application il a un sens métier : un accès en lecture seule à la gestion.

Comptes de démonstration (profil `dev`, usage local uniquement, volontairement absents de l'écran de
connexion) :

| Compte | Mot de passe | Rôle |
|---|---|---|
| `camille.martin@example.com` | `client123` | CLIENT (créé par `DemoDataInitializer`) |
| `admin` | `admin123` | ADMIN |
| `student` | `student123` | USER |

N'importe qui peut aussi créer un compte client avec « Créer un compte ».

## Parcours client

1. **Inscription** (`POST /api/client/inscription`, ouvert aux anonymes) : identité, email, permis,
   mot de passe (8 caractères minimum). Email et permis uniques.
2. **Recherche** : dates de départ et de retour, catégorie. Seuls les véhicules **disponibles** sont
   proposés : pas en maintenance, et sans réservation (non annulée) qui chevauche la période.
   Le prix total est calculé : tarif journalier × nombre de jours (bornes incluses).
3. **Louer** : récapitulatif, mode de paiement, acceptation des conditions, « Payer ».
   Le serveur crée en **une transaction** : la réservation (CONFIRMEE), le contrat (validé) et le
   paiement du montant total. Un seul `save(reservation)` suffit grâce aux cascades
   `Reservation → Contrat (ALL)` puis `Contrat → Paiement (ALL)`.
4. **Mes locations** : historique avec contrat, montant payé et statut. Annulation possible tant que la
   location n'a pas commencé (statut ANNULEE, contrat invalidé, véhicule de nouveau disponible).
5. **Mon profil** : prénom, nom, téléphone modifiables ; email et permis non modifiables.

Règles vérifiées côté serveur : dates dans le futur, fin après début, 60 jours maximum, véhicule
disponible (sinon **409**), une location ne peut être vue ou annulée que par **son** client (sinon 404).
Le client connecté est toujours lu dans l'authentification (`Authentication.getName()`), jamais dans la
requête : impossible d'agir au nom d'un autre client.

## Architecture

```
navigateur ── index.html + css/app.css + js/app.js     (src/main/resources/static, publics)
     │  fetch + en-tête Authorization: Basic ...
     ▼
Spring Security
  ├─ POST /api/client/inscription   → tout le monde
  ├─ /api/client/**                 → CLIENT
  ├─ /api/me, /api/enums            → tout utilisateur connecté
  ├─ GET /api/console/**            → USER ou ADMIN
  └─ POST/PUT/DELETE /api/console/** → ADMIN
     ▼
fr.ece.autoloc.espaceclient   EspaceClientController → EspaceClientService (@Service, @Transactional)
fr.ece.autoloc.console        CrudResource<E, D> + une ressource par entité, ConsoleController (/stats)
fr.ece.autoloc.web.controller SessionController (/me, /enums)
     ▼
repository (JpaRepository, méthodes dérivées, @Query de chevauchement) ── Hibernate ── MySQL
```

### Comptes : deux sources dans un seul `UserDetailsService`

`SecurityConfig.userDetailsService` cherche d'abord le personnel en mémoire (`admin`, `student`), puis
un client en base via `ClientRepository.findByEmailIgnoreCase`. Un client trouvé avec un mot de passe
devient un `UserDetails` avec le rôle `CLIENT`. C'est la réponse concrète à la question de l'atelier 2
« que faudrait-il pour charger les utilisateurs depuis MySQL ? ». Un client créé au guichet par l'agence
(sans mot de passe) n'a pas d'accès en ligne.

### Routes de l'espace client

| Méthode | Route | Effet |
|---|---|---|
| POST | `/api/client/inscription` | créer un compte (201, 400, 409) |
| GET / PUT | `/api/client/profil` | lire / modifier son profil |
| GET | `/api/client/vehicules?debut=&fin=&categorie=` | véhicules disponibles et prix |
| GET | `/api/client/locations` | ses locations |
| POST | `/api/client/locations` | louer et payer (201, 400, 409) |
| POST | `/api/client/locations/{id}/annulation` | annuler (200, 404, 409) |

### Routes de la console (ADMIN / USER)

Pour `agences`, `employes`, `equipements`, `vehicules`, `maintenances`, `clients`, `reservations`,
`contrats`, `paiements` : `GET /api/console/{ressource}`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.
Plus `GET /api/console/stats` pour le tableau de bord.

## Choix techniques (à savoir expliquer)

- **DTO plutôt qu'entités** : les entités ont des associations LAZY et bidirectionnelles ; les sérialiser
  provoquerait des `LazyInitializationException` (`open-in-view=false`) ou des boucles infinies.
  Les données sont converties en `record` à l'intérieur de la transaction.
- **Couche service pour l'espace client** : les règles métier (disponibilité, prix, annulation) sont dans
  `EspaceClientService`, annoté `@Transactional` ; le contrôleur ne fait que recevoir et répondre.
- **Cascade visible** : louer persiste 3 entités en un `save` ; supprimer une réservation dans la console
  supprime son contrat puis ses paiements ; supprimer une agence ou un client encore utilisé est refusé (409).
- **Cas particuliers de la console** : `Equipement` (côté inverse du `@ManyToMany`) est retiré des véhicules
  avant suppression ; `Contrat` est détaché de sa réservation avant suppression ; `Paiement` change de
  contrat par le seul côté propriétaire (sinon `orphanRemoval` le supprimerait).
- **Mot de passe** : jamais renvoyé par l'API ni affiché ; seul le hash BCrypt est stocké
  (colonne `client.mot_de_passe`, ajoutée par `ddl-auto=update` en dev).
- **Sécurité** : HTTP Basic sans session (`STATELESS`), donc CSRF ignoré sur `/api/**`. L'en-tête
  `X-Requested-With: XMLHttpRequest` évite la fenêtre de connexion du navigateur. En production :
  HTTPS obligatoire, et plutôt des jetons (JWT).
