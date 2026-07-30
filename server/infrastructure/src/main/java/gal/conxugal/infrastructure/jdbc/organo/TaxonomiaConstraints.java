package gal.conxugal.infrastructure.jdbc.organo;

import io.micronaut.data.exceptions.DataAccessException;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Recognises the taxonomy's database guards in a failed statement, so the adapters can raise
 * the same domain refusal for a raced write that a checked one raises. This is the only place
 * in the application that knows a SQLSTATE; nothing above {@code infrastructure} sees one.
 *
 * <p>Recognition pins the SQLSTATE <em>and</em> the constraint name, never the message text: a
 * message is prose that a server locale or a version bump may reword, whereas both of these
 * are part of the wire protocol. Pinning the name as well as the state is what stops a
 * different constraint failing for its own reasons from being reported as this one.
 */
final class TaxonomiaConstraints {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";

  private static final String SIBLING_NAME_INDEX = "termo_sibling_name_key";
  private static final String PARENT_FOREIGN_KEY = "termo_parent_id_fkey";
  private static final String PLACEMENT_FOREIGN_KEY = "organo_contratacion_termo_id_fkey";

  private TaxonomiaConstraints() {}

  /** A taxonomy guard a statement can be refused by, named for what it protects. */
  enum Guard {
    /** The unique index over {@code (parent_id, lower(name))} — the sibling-name rule. */
    SIBLING_NAME,
    /** {@code termo.parent_id} pointing at a term that isn't there, or still has children. */
    PARENT_REFERENCE,
    /** {@code organo_contratacion.termo_id} pointing at a term that isn't there, or in use. */
    PLACEMENT_REFERENCE,
    /** No guard we recognise — the caller lets the failure through untranslated. */
    NONE
  }

  /** The guard that refused the statement, or {@link Guard#NONE} for anything else. */
  static Guard refusedBy(DataAccessException failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof PSQLException postgres) {
        Guard guard = guardOf(postgres);
        if (guard != null) {
          return guard;
        }
      }
    }
    return Guard.NONE;
  }

  private static @Nullable Guard guardOf(PSQLException failure) {
    ServerErrorMessage error = failure.getServerErrorMessage();
    if (error == null) {
      return null;
    }
    String state = failure.getSQLState();
    String constraint = error.getConstraint();
    if (UNIQUE_VIOLATION.equals(state) && SIBLING_NAME_INDEX.equals(constraint)) {
      return Guard.SIBLING_NAME;
    }
    if (FOREIGN_KEY_VIOLATION.equals(state)) {
      if (PARENT_FOREIGN_KEY.equals(constraint)) {
        return Guard.PARENT_REFERENCE;
      }
      if (PLACEMENT_FOREIGN_KEY.equals(constraint)) {
        return Guard.PLACEMENT_REFERENCE;
      }
    }
    return null;
  }
}
