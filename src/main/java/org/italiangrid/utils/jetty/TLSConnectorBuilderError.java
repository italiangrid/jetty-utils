package org.italiangrid.utils.jetty;

public class TLSConnectorBuilderError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TLSConnectorBuilderError(Throwable cause) {
    super(cause);
  }

  public TLSConnectorBuilderError(String message, Throwable cause) {
    super(message, cause);
  }

  public TLSConnectorBuilderError(String message) {
    super(message);
  }

}
