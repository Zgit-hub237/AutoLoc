# Bonus : application web AutoLoc (site public, espace client, console de l'agence)

Une interface web servie par l'application Spring Boot elle-même (HTML/CSS/JavaScript natifs, sans
framework ni Node). Elle a trois parties :

1. **Site public** (sans connexion) : présentation, recherche des véhicules disponibles avec le prix
   total, explication du fonctionnement, liste des agences.
2. **Espace client** : la connexion n'est demandée qu'au moment de **louer**. Le visiteur qui clique sur
   « Louer » se connecte (ou crée son compte), puis revient directement au paiement du véhicule choisi.
3. **Console de l'agence** (ADMIN, USER) : gestion des 9 entités et tableau de bord.

Les illustrations (véhicules par catégorie, façades d'agence) sont des dessins vectoriels SVG générés
par `js/app.js` : aucune image externe, aucun droit d'auteur, couleur stable par véhicule
(calculée depuis l'immatriculation).

## Lancer

1. Démarrer MySQL dans XAMPP.
2. Lancer `AutolocApiApplication` dans IntelliJ (ou `./mvnw spring-boot:run`).
3. Ouvrir **http://localhost:8080**.

## Les rôles

| Rôle | Qui | Connexion | Ce qu'il peut faire |
|---|---|---|---|
| `CLIENT` | un client | son **email** + mot de passe (inscription en ligne, ou accès ouvert au guichet) | louer et payer, suivre et annuler ses locations, modifier son profil |
| `AGENT` | employé de rôle AGENT | identifiant + mot de passe définis par l'administrateur | console : tout lire ; créer et modifier clients, réservations, contrats, paiements, maintenances ; **jamais supprimer** |
| `MANAGER` | employé de rôle MANAGER | identifiant + mot de passe définis par l'administrateur | toute la console (flotte comprise, suppressions comprises) **sauf** agences et employés (lecture) |
| `ADMIN` | administrateur | compte interne | tout, y compris les agences et les **comptes des employés** |
| `USER` | collaborateur en consultation | compte interne | toute la console en **lecture seule** |

Les droits sont appliqués **par le serveur** (règles de `SecurityConfig`, du plus précis au plus large) ;
l'interface se contente de masquer les boutons inutiles (`can()` dans `js/app.js`).

| Requête console | USER | AGENT | MANAGER | ADMIN |
|---|---|---|---|---|
| `GET /api/console/**` | oui | oui | oui | oui |
| écrire agences, employés | non | non | non | oui |
| `DELETE /api/console/**` | non | non | oui | oui |
| écrire véhicules, équipements | non | non | oui | oui |
| écrire clients, réservations, contrats, paiements, maintenances | non | oui | oui | oui |

**Comptes des employés** : à la création (ou modification) d'un employé, l'administrateur saisit un
identifiant (3 à 30 caractères `a-z 0-9 . _ -`, unique, différent de `admin`/`student`) et un mot de passe
(8 caractères minimum, stocké haché BCrypt). Sans identifiant, l'employé n'a pas d'accès. Le rôle
de l'employé (`AGENT`/`MANAGER`) devient son rôle Spring Security à la connexion.

Le compte `USER` (`student`) vient de l'atelier 2 : c'est lui qui démontre le **403** (« authentifié mais
pas autorisé »). Dans l'application il a un sens métier : un accès en lecture seule à la gestion.

Comptes de démonstration (profil `dev`, usage local uniquement, volontairement absents de l'écran de
connexion) :

| Compte | Mot de passe | Rôle |
|---|---|---|
| `camille.martin@example.com` | `client123` | CLIENT |
| `agent` | `agent123` | AGENT (Hugo Petit) |
| `manager` | `manager123` | MANAGER (Julie Moreau) |
| `admin` | `admin123` | ADMIN |
| `student` | `student123` | USER |

## Statut des véhicules : calculé, jamais saisi

Le statut d'un véhicule décrit **aujourd'hui** ; il est calculé par `service/FlotteService` :

| Statut | Condition |
|---|---|
| `MAINTENANCE` | une maintenance couvre aujourd'hui (sans date de fin = encore en cours) |
| `LOUE` | une réservation **CONFIRMEE** couvre aujourd'hui |
| `RESERVE` | une réservation (en attente ou confirmée) est prévue à une date ultérieure |
| `DISPONIBLE` | sinon |

- Recalculé après chaque location, annulation, réservation ou maintenance (console et espace client).
- Tâche planifiée chaque nuit à 00:05 (`@Scheduled`) et à chaque démarrage : les réservations confirmées
  dont la fin est passée deviennent **TERMINEE**, celles restées EN_ATTENTE après leur début deviennent
  **ANNULEE**, puis tous les statuts sont recalculés.
- Une location réservée pour plus tard rend le véhicule **RESERVE dès la réservation**, puis **LOUE le jour
  du départ** ; après le retour il redevient DISPONIBLE (ou RESERVE s'il a une autre location prévue).
- Un véhicule RESERVE reste louable **à d'autres dates** : la disponibilité d'une période se vérifie par
  chevauchement (réservations actives + maintenances), pas par le statut.
- Dans la console, le champ Statut est donc en lecture seule.

## Règles de cohérence

- Pas de double réservation : une réservation active (EN_ATTENTE ou CONFIRMEE) ne peut pas chevaucher une
  autre réservation active ni une maintenance du même véhicule (console **et** espace client, 409).
- Annuler une réservation invalide son contrat (gardé pour l'historique).
- Le total des paiements d'un contrat ne peut pas dépasser son montant (400) ; montants et tarifs > 0.
- Un contrat ne peut pas être validé pour une réservation annulée.
- Un client créé au guichet peut recevoir un accès en ligne (mot de passe saisi par l'agent).

## Parcours client

0. **Visite** (anonyme) : `GET /api/public/vehicules?debut=&fin=&categorie=` et `GET /api/public/agences`,
   ouverts à tous comme les valeurs d'enums (`GET /api/enums`). Tout le reste de l'API exige une connexion.
1. **Inscription** (`POST /api/client/inscription`, ouvert aux anonymes) : identité, email, permis,
   mot de passe (8 caractères minimum). Email et permis uniques.
2. **Recherche** : dates de départ et de retour, catégorie. Seuls les véhicules **disponibles** sont
   proposés : sans maintenance ni réservation active (en attente ou confirmée) qui chevauche la période.
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
  ├─ /api/public/**, GET /api/enums → tout le monde (site public)
  ├─ POST /api/client/inscription   → tout le monde
  ├─ /api/client/**                 → CLIENT
  ├─ /api/me                        → tout utilisateur connecté
  ├─ GET /api/console/**            → USER ou ADMIN
  └─ POST/PUT/DELETE /api/console/** → ADMIN
     ▼
fr.ece.autoloc.espaceclient   CatalogueController (public) + EspaceClientController → EspaceClientService
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
