package gal.conxugal.domain.user;

import java.util.Objects;

/** The account just created by {@link CreateUser}, with its one-time initial password. */
public record CreatedAccount(User user, GeneratedPassword initialPassword) {

  public CreatedAccount {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(initialPassword, "initialPassword must not be null");
  }
}
