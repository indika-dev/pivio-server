package io.pivio.server.document;

/**
 * MandatoryFieldMissingOrEmptyException
 */
public class MandatoryFieldMissingOrEmptyException extends RuntimeException {

  public MandatoryFieldMissingOrEmptyException(String msg) {
    super(msg);
  }

}
