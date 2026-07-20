package gal.conxugal.domain.user;

/**
 * A user's role. {@code ADMIN} is a strict superset of {@code USER}: any capability
 * granted to {@code USER} is also granted to {@code ADMIN}.
 */
public enum Role {
  USER,
  ADMIN
}
