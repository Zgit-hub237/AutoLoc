-- Jeu de données de démonstration AutoLoc (base autoloc_db)
-- ATTENTION : la section 0 vide toutes les tables avant de réinsérer les données.

USE autoloc_db;

-- 0. Remise à zéro (ordre inverse des clés étrangères)
DELETE FROM paiement;
DELETE FROM contrat;
DELETE FROM reservation;
DELETE FROM maintenance;
DELETE FROM vehicule_equipement;
DELETE FROM vehicule;
DELETE FROM employe;
DELETE FROM equipement;
DELETE FROM client;
DELETE FROM agence;

-- 1. Agences
INSERT INTO agence (nom, ville, adresse, telephone) VALUES
  ('AutoLoc Paris Gare de Lyon', 'Paris', '20 boulevard Diderot', '0140000001'),
  ('AutoLoc Lyon Part-Dieu',     'Lyon',  '5 place Charles Béraudier', '0472000002');

-- 2. Employés (Agence 1 — * Employe)
INSERT INTO employe (nom, prenom, role, agence_id_agence) VALUES
  ('Martin', 'Sophie', 'MANAGER', (SELECT id_agence FROM agence WHERE ville = 'Paris')),
  ('Durand', 'Karim',  'AGENT',   (SELECT id_agence FROM agence WHERE ville = 'Paris')),
  ('Petit',  'Lucie',  'AGENT',   (SELECT id_agence FROM agence WHERE ville = 'Lyon'));

-- 3. Véhicules (Agence 1 — * Vehicule)
INSERT INTO vehicule (immatriculation, marque, modele, categorie, tarif_journalier, statut, agence_id_agence) VALUES
  ('AB-123-CD', 'Renault', 'Clio',    'CITADINE',   45.00, 'DISPONIBLE',  (SELECT id_agence FROM agence WHERE ville = 'Paris')),
  ('EF-456-GH', 'Peugeot', '3008',    'SUV',        79.90, 'LOUE',        (SELECT id_agence FROM agence WHERE ville = 'Paris')),
  ('IJ-789-KL', 'Citroën', 'Jumpy',   'UTILITAIRE', 95.00, 'MAINTENANCE', (SELECT id_agence FROM agence WHERE ville = 'Lyon'));

-- 4. Équipements + table de jointure (Vehicule * — * Equipement)
INSERT INTO equipement (libelle) VALUES ('GPS'), ('Siège bébé'), ('Chaînes neige');

INSERT INTO vehicule_equipement (id_vehicule, id_equipement)
SELECT v.id_vehicule, e.id_equipement
FROM vehicule v JOIN equipement e
WHERE (v.immatriculation = 'AB-123-CD' AND e.libelle IN ('GPS', 'Siège bébé'))
   OR (v.immatriculation = 'EF-456-GH' AND e.libelle IN ('GPS', 'Chaînes neige'))
   OR (v.immatriculation = 'IJ-789-KL' AND e.libelle = 'GPS');

-- 5. Clients
INSERT INTO client (nom, prenom, email, telephone, num_permis, date_inscription) VALUES
  ('Bernard', 'Alice', 'alice.bernard@example.com', '0600000001', 'PERMIS-0001', '2026-09-01'),
  ('Nguyen',  'Tom',   'tom.nguyen@example.com',    '0600000002', 'PERMIS-0002', '2026-10-15');

-- 6. Réservations (Client 1 — * Reservation, Reservation * — 1 Vehicule)
INSERT INTO reservation (date_debut, date_fin, statut, client_id_client, vehicule_id_vehicule) VALUES
  ('2026-11-02', '2026-11-05', 'TERMINEE',
     (SELECT id_client FROM client WHERE num_permis = 'PERMIS-0001'),
     (SELECT id_vehicule FROM vehicule WHERE immatriculation = 'AB-123-CD')),
  ('2026-11-10', '2026-11-14', 'CONFIRMEE',
     (SELECT id_client FROM client WHERE num_permis = 'PERMIS-0001'),
     (SELECT id_vehicule FROM vehicule WHERE immatriculation = 'EF-456-GH')),
  ('2026-12-20', '2026-12-27', 'EN_ATTENTE',
     (SELECT id_client FROM client WHERE num_permis = 'PERMIS-0002'),
     (SELECT id_vehicule FROM vehicule WHERE immatriculation = 'AB-123-CD'));

-- 7. Contrats (Reservation 1 — 0..1 Contrat) : la réservation EN_ATTENTE n'a pas encore de contrat
INSERT INTO contrat (date_signature, montant_total, valide, reservation_id_reservation)
SELECT r.date_debut, DATEDIFF(r.date_fin, r.date_debut) * v.tarif_journalier, TRUE, r.id_reservation
FROM reservation r JOIN vehicule v ON v.id_vehicule = r.vehicule_id_vehicule
WHERE r.statut IN ('TERMINEE', 'CONFIRMEE');

-- 8. Paiements (Contrat 1 — * Paiement) : un contrat soldé en 1 fois, l'autre en 2 fois
INSERT INTO paiement (montant, date_paiement, mode_paiement, contrat_id_contrat)
SELECT c.montant_total, c.date_signature, 'CARTE', c.id_contrat
FROM contrat c JOIN reservation r ON r.id_reservation = c.reservation_id_reservation
WHERE r.statut = 'TERMINEE';

INSERT INTO paiement (montant, date_paiement, mode_paiement, contrat_id_contrat)
SELECT c.montant_total / 2, c.date_signature, m.mode, c.id_contrat
FROM contrat c
JOIN reservation r ON r.id_reservation = c.reservation_id_reservation
JOIN (SELECT 'VIREMENT' AS mode UNION ALL SELECT 'ESPECES') m
WHERE r.statut = 'CONFIRMEE';

-- 9. Maintenances (Vehicule 1 — * Maintenance) : une terminée, une en cours (date_fin NULL)
INSERT INTO maintenance (date_debut, date_fin, description, vehicule_id_vehicule) VALUES
  ('2026-10-01', '2026-10-02', 'Vidange et filtres', (SELECT id_vehicule FROM vehicule WHERE immatriculation = 'AB-123-CD')),
  ('2026-11-08', NULL,         'Remplacement embrayage', (SELECT id_vehicule FROM vehicule WHERE immatriculation = 'IJ-789-KL'));
