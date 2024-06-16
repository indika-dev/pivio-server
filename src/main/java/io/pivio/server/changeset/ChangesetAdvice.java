package io.pivio.server.changeset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import io.pivio.server.document.MandatoryFieldMissingOrEmptyException;

/**
 * ChangesetAdvice
 */
@RestControllerAdvice
public class ChangesetAdvice {

  @ExceptionHandler(InvalidSinceParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String invalidSinceParameterHandler(InvalidSinceParameterException e) {
    return e.getMessage();
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String documentNotFoundHandler(DocumentNotFoundException e) {
    return e.getMessage();
  }

  @ExceptionHandler(MandatoryFieldMissingOrEmptyException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String mandatoryFieldMissingOrEmptyHandler(MandatoryFieldMissingOrEmptyException e) {
    return e.getMessage();
  }
}
