# Bonus : console web de gestion AutoLoc

Une interface web pour manipuler tout le système : consulter, créer, modifier et supprimer
les 9 entités, avec un tableau de bord. Elle est servie par l'application Spring Boot elle-même,
sans outil supplémentaire (pas de Node, pas de framework front).

## Lancer

1. Démarrer MySQL dans XAMPP.
2. Lancer `AutolocApiApplication` dans IntelliJ (ou `./mvnw spring-boot:run`).
3. Ouvrir **http://localhost:8080** dans le navigateur.

| Compte | Mot de passe | Droits dans la console |
|---|---|---|
| `admin` | `admin123` | tout : création, modification, suppression |
| `student` | `student123` | consultation seule (boutons d'écriture masqués, et l'API répond 403) |

## Architecture

```
navigateur ── index.html + css/app.css + js/app.js    (src/main/resources/static, publics)
     │  fetch + en-tête Authorization: Basic ...
     ▼
Spring Security ── GET /api/console/**  → USER ou ADMIN
                └─ POST/PUT/DELETE      → ADMIN uniquement
     ▼
fr.ece.autoloc.console
  ├── CrudResource<E, D>       base générique : list, get, create, update, delete (@Transactional)
  ├── VehiculeResource, ...    une classe par entité : DTO (record) + conversion + règles
  ├── ConsoleController        /me (utilisateur connecté), /enums, /stats (tableau de bord)
  └── ConsoleExceptionHandler  erreurs → JSON {"message": "..."} lisible dans l'interface
     ▼
repository (JpaRepository) ── Hibernate ── MySQL
```

### Routes de l'API

Pour chaque ressource `agences`, `employes`, `equipements`, `vehicules`, `maintenances`, `clients`,
`reservations`, `contrats`, `paiements` :

| Méthode | Route | Effet | Code |
|---|---|---|---|
| GET | `/api/console/{ressource}` | liste | 200 |
| GET | `/api/console/{ressource}/{id}` | détail | 200 / 404 |
| POST | `/api/console/{ressource}` | création | 201 / 400 / 409 |
| PUT | `/api/console/{ressource}/{id}` | modification | 200 / 400 / 404 / 409 |
| DELETE | `/api/console/{ressource}/{id}` | suppression | 204 / 404 / 409 |

Plus `GET /api/console/me`, `/api/console/enums` et `/api/console/stats`.

## Choix techniques (à savoir expliquer)

- **DTO plutôt qu'entités** : les entités ont des associations LAZY et bidirectionnelles. Les sérialiser
  directement provoquerait des `LazyInitializationException` (avec `open-in-view=false`) ou des boucles
  infinies. Chaque ressource convertit l'entité en `record` plat (associations exposées par leur id et
  un libellé), **à l'intérieur de la transaction**.
- **Associations reçues par id** : le formulaire envoie `agenceId`, `equipementIds`… ; le serveur charge
  l'entité correspondante (`findById`) et l'affecte au **côté propriétaire**.
- **Cascade visible dans l'interface** : supprimer une réservation supprime son contrat puis ses paiements
  (`CascadeType.ALL` + `orphanRemoval`) ; supprimer une agence ou un client encore utilisé est refusé
  (aucune cascade de suppression) avec un message 409 clair. Le texte de confirmation l'indique.
- **Cas particuliers gérés** :
  - `Equipement` est le côté inverse du `@ManyToMany` : avant suppression, on le retire des véhicules
    (sinon la clé étrangère de `vehicule_equipement` bloque).
  - `Contrat` : avant suppression, on le détache de sa réservation, sinon la cascade ALL de
    `Reservation.contrat` le ré-enregistrerait au flush.
  - `Paiement` : on modifie seulement le côté propriétaire (`setContrat`), car le retirer de la collection
    de l'ancien contrat déclencherait `orphanRemoval`.
- **`saveAndFlush` / `flush`** : force l'envoi du SQL dans la méthode, pour transformer immédiatement une
  violation de contrainte (doublon, clé étrangère) en réponse 409 compréhensible.
- **Sécurité** : l'API reste en HTTP Basic, sans session. L'en-tête `X-Requested-With: XMLHttpRequest`
  demande à Spring Security un simple 401, sans fenêtre de connexion du navigateur.
- **Interface** : HTML/CSS/JavaScript natifs. Les écrans sont générés à partir d'une description de chaque
  entité (colonnes, champs, types) dans `js/app.js` ; les valeurs des listes déroulantes d'enums viennent
  du serveur (`/enums`), elles restent donc toujours alignées sur le code Java.

## Fonctionnalités

- Tableau de bord : indicateurs, état de la flotte, réservations par statut, réservations à venir,
  maintenances en cours.
- Pour chaque entité : tableau triable, recherche, filtre par statut / rôle / mode, formulaire latéral
  de création et de modification, suppression avec confirmation.
- Sélection des équipements d'un véhicule (`@ManyToMany`), suivi de l'encaissement d'un contrat.
- Interface adaptée au rôle : consultation seule pour `student`.
- Utilisable sur mobile (menu repliable).
