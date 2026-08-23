package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

/**
 * What the migration tests assert when the schema refuses a write. They need the same handful of
 * checks, and the same reason for making them: the SQLSTATE says which <em>kind</em> of
 * constraint fired, and pinning the constraint name beside it rules out a coincidental different
 * constraint failing for the wrong reason and reading as a pass.
 *
 * <p>Shared rather than copied, because assertions duplicated across three classes are ones that
 * can come to disagree about what a refusal looks like.
 */
final class Refusals {

  /** SQLSTATE 23505 is unique_violation. */
  private static final String UNIQUE_VIOLATION = "23505";

  /** SQLSTATE 23503 is foreign_key_violation — the write was refused rather than cascading. */
  private static final String FOREIGN_KEY_VIOLATION = "23503";

  /** SQLSTATE 23502 is not_null_violation. */
  private static final String NOT_NULL_VIOLATION = "23502";

  /** SQLSTATE 23514 is check_violation. */
  private static final String CHECK_VIOLATION = "23514";

  private Refusals() {
  }

  static void violatesUniqueness(SQLException exception, String constraint) {
    assertViolates(exception, UNIQUE_VIOLATION, constraint);
  }

  /**
   * Uniqueness alone, for the one case whose point is that the write is a duplicate at all rather
   * than which constraint says so.
   */
  static void violatesUniqueness(SQLException exception) {
    assertThat(exception.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
  }

  static void violatesCheck(SQLException exception, String constraint) {
    assertViolates(exception, CHECK_VIOLATION, constraint);
  }

  static void violatesForeignKey(SQLException exception, String constraint) {
    assertViolates(exception, FOREIGN_KEY_VIOLATION, constraint);
  }

  /**
   * The one refusal with no constraint to name: a not-null violation names the column, and the
   * column is what the test already says it is asserting about.
   */
  static void violatesNotNull(SQLException exception) {
    assertThat(exception.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
  }

  private static void assertViolates(SQLException exception, String sqlState, String constraint) {
    assertThat(exception.getSQLState()).isEqualTo(sqlState);
    assertThat(exception.getMessage()).contains(constraint);
  }
}
