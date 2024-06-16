package io.pivio.server.changeset;

public class DocumentNotFoundException extends RuntimeException {

  public DocumentNotFoundException(String id) {
    super("document with id " + id + " not found");
  }

}

