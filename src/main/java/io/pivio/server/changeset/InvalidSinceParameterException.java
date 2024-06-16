package io.pivio.server.changeset;

/**
 * InvalidSinceParameter
 */
public class InvalidSinceParameterException extends RuntimeException {

  InvalidSinceParameterException(String since) {
    super(since + " is not a valid parameter");
  }

}
