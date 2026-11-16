package fr.ece.autoloc.console;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Turns errors into a JSON body {"message": "..."} that the console displays as is. */
@Slf4j
@RestControllerAdvice(basePackages = "fr.ece.autoloc.console")
public class ConsoleExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", String.valueOf(e.getReason())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        log.debug("Integrity violation: {}", cause);
        String message;
        if (cause.contains("Duplicate")) {
            message = "Cette valeur existe déjà : le champ doit être unique (immatriculation, email, permis, "
                    + "ou un contrat déjà rattaché à cette réservation).";
        } else if (cause.contains("foreign key")) {
            message = "Suppression impossible : cet élément est encore utilisé par d'autres données. "
                    + "Supprimez ou réaffectez-les d'abord.";
        } else {
            message = "Opération refusée par la base de données (contrainte non respectée).";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Données invalides : vérifiez le format des champs."));
    }
}
