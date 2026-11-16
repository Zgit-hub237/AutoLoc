package fr.ece.autoloc.domain;

/**
 * Current state of a vehicle, computed by FlotteService (never typed by hand):
 * DISPONIBLE no rental or maintenance planned, RESERVE a rental is booked for later,
 * LOUE a rental is in progress today, MAINTENANCE a maintenance is in progress today.
 */
public enum StatutVehicule {
    DISPONIBLE, RESERVE, LOUE, MAINTENANCE
}
