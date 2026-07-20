package gal.conxugal.domain.auth;

import java.util.Objects;

/**
 * A freshly generated password. {@code value} is the plaintext, readable only through
 * this object; it is never stored, logged or retrievable afterwards.
 */
public record GeneratedPassword(String value) {

  public GeneratedPassword {
    Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public String toString() {
    return "GeneratedPassword[value=REDACTED]";
  }
}
