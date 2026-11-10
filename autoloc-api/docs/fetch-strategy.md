# Stratégie de chargement (fetch) et de cascade — AutoLoc

Ce document justifie, pour chaque association JPA du modèle AutoLoc, le côté propriétaire,
la stratégie de chargement (`FetchType`) et la propagation des opérations (`CascadeType`).

## Règles générales

- **LAZY partout.** Les associations `@ManyToOne` et `@OneToOne` sont EAGER par défaut en JPA ;
  on les passe explicitement en LAZY pour éviter de charger des chaînes d'objets
  (contrat → réservation → véhicule → agence…) à chaque lecture. Les collections restent LAZY.
  Quand des données liées sont nécessaires, on les charge explicitement (`join fetch` ou projection DTO).
- **La cascade suit le cycle de vie.** On ne propage la suppression (`REMOVE` / `ALL`) que lorsque
  l'enfant n'a pas d'existence propre (composition UML). Une entité partagée ou historisée
  n'est jamais supprimée en cascade.
- **Le côté `@ManyToOne` est propriétaire** et porte la clé étrangère ; le côté `@OneToMany`
  utilise `mappedBy` et n'ajoute aucune colonne ni table.

## Tableau de synthèse

| Association | Multiplicité | Côté propriétaire (FK / table) | Fetch | Cascade | Justification |
|---|---|---|---|---|---|
| Contrat → Paiement | 1 — * (composition) | `Paiement` : `paiement.contrat_id_contrat` | LAZY | ALL + orphanRemoval | Un paiement n'existe pas sans son contrat : il est créé, modifié et supprimé avec lui ; retiré de la collection, il est supprimé. |
| Agence → Vehicule | 1 — * | `Vehicule` : `vehicule.agence_id_agence` | LAZY | Aucune | Un véhicule survit à son agence et peut changer d'agence ; `orphanRemoval` le supprimerait lors d'une réaffectation. |
| Agence → Employe | 1 — * | `Employe` : `employe.agence_id_agence` | LAZY | Aucune | Un employé a sa propre existence (mutation, départ) ; supprimer une agence ne doit pas supprimer ses salariés. |
| Vehicule ↔ Equipement | * — * | `Vehicule` : table `vehicule_equipement` | LAZY | Aucune | Un équipement est partagé entre plusieurs véhicules ; seule la ligne de liaison disparaît. `Set` évite les doublons et les suppressions/réinsertions d'une `List`. |
| Client → Reservation | 1 — * | `Reservation` : `reservation.client_id_client` | LAZY | PERSIST | On peut enregistrer un nouveau client avec ses premières réservations ; les réservations forment un historique et ne sont pas supprimées avec le client. |
| Reservation → Vehicule | * — 1 (unidirectionnelle) | `Reservation` : `reservation.vehicule_id_vehicule` | LAZY | Aucune | La réservation référence le véhicule loué sans en gérer le cycle de vie ; les réservations d'un véhicule seront obtenues par requête. |
| Reservation ↔ Contrat | 1 — 0..1 | `Contrat` : `contrat.reservation_id_reservation` (UNIQUE) | LAZY | ALL côté Reservation | Le contrat dépend de la réservation ; supprimer la réservation supprime son contrat puis, en chaîne, ses paiements. |
| Vehicule → Maintenance | 1 — * | `Maintenance` : `maintenance.vehicule_id_vehicule` | LAZY | PERSIST | On peut enregistrer un véhicule avec ses premières maintenances ; l'historique d'entretien est conservé et n'est pas supprimé en cascade. |

## Observations faites en TP (Contrat → Paiement)

Mesures obtenues avec `CascadeContratPaiementTest` sur la base `autoloc_db` :

| Mode | SQL généré par `findById(contrat)` | Paiements chargés ? |
|---|---|---|
| LAZY | `select … from contrat where id_contrat=?` | non (proxy, chargé à l'accès) |
| EAGER | `select … from contrat left join paiement on … where id_contrat=?` | oui |

- **Cascade PERSIST** : un seul `save(contrat)` génère 1 `insert` contrat + 1 `insert` par paiement.
- **orphanRemoval** : retirer un paiement de la collection génère `delete from paiement`.
- **Cascade REMOVE** : `deleteById(contrat)` supprime d'abord les paiements, puis le contrat.

## Points d'attention

- **LazyInitializationException** : accéder à une collection LAZY après la fermeture de la
  transaction lève cette exception ; il faut charger les données nécessaires dans la transaction.
- **`@OneToOne` côté inverse** : sur `Reservation.contrat` (`mappedBy`), Hibernate ne peut pas
  toujours respecter LAZY, car il doit interroger `contrat` pour savoir si la valeur est `null`.
- **PERSIST et `save()`** : `save()` appelle `persist` pour une entité nouvelle mais `merge`
  pour une entité existante ; avec `cascade = PERSIST` seul, une réservation ajoutée à un client
  déjà en base doit être enregistrée explicitement.
- **Pas de `@Data`** sur les entités liées : `toString`/`equals`/`hashCode` générés parcourraient
  les associations bidirectionnelles à l'infini. On utilise `@Getter` / `@Setter` ciblés.
