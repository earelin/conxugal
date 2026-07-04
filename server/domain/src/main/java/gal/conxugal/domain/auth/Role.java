package gal.conxugal.domain.auth;

/**
 * A user's role (SPEC-0002). {@code ADMIN} is a strict superset of {@code USER}: any
 * capability granted to {@code USER} is also granted to {@code ADMIN}.
 */
public enum Role {
    USER,
    ADMIN
}
