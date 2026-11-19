package com.fieldflow.api;

import com.fieldflow.api.dto.ErreurDto;
import com.fieldflow.regles.Decision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Codes HTTP de contracts/api-rest.md : INVALIDE → 400, INCONNU → 404, MAUVAIS_ETAT → 409. */
@RestControllerAdvice
public class GestionErreurs {

    @ExceptionHandler(RegleRefuseeException.class)
    ResponseEntity<ErreurDto> regleRefusee(RegleRefuseeException e) {
        Decision d = e.decision();
        HttpStatus statut = switch (d.type()) {
            case INVALIDE -> HttpStatus.BAD_REQUEST;
            case INCONNU -> HttpStatus.NOT_FOUND;
            case MAUVAIS_ETAT -> HttpStatus.CONFLICT;
            case OK -> throw new IllegalStateException("Une décision OK n'est pas une erreur");
        };
        return ResponseEntity.status(statut).body(new ErreurDto(d.code(), d.message()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErreurDto> requeteInvalide(Exception e) {
        return ResponseEntity.badRequest().body(new ErreurDto("CHAMP_INVALIDE", "Requête invalide : champ manquant ou incorrect."));
    }
}
