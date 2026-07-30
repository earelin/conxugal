package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.data.exceptions.DataAccessException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

/**
 * The constraint names and SQLSTATEs are spelled out again here rather than read off
 * {@link TaxonomiaConstraints}. Sharing them would make the test agree with whatever the
 * translator happens to say, including a typo; written out, these assertions are what pins
 * the translator to the names V9 actually created.
 */
class TaxonomiaConstraintsTest {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";
  private static final String CHECK_VIOLATION = "23514";

  private static final String SIBLING_NAME_INDEX = "termo_sibling_name_key";
  private static final String PARENT_FOREIGN_KEY = "termo_parent_id_fkey";
  private static final String PLACEMENT_FOREIGN_KEY = "organo_contratacion_termo_id_fkey";

  @Test
  void recognises_the_sibling_name_index() {
    DataAccessException failure = failureViolating(UNIQUE_VIOLATION, SIBLING_NAME_INDEX);

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.SIBLING_NAME);
  }

  @Test
  void recognises_the_parent_foreign_key() {
    DataAccessException failure = failureViolating(FOREIGN_KEY_VIOLATION, PARENT_FOREIGN_KEY);

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.PARENT_REFERENCE);
  }

  @Test
  void recognises_the_placement_foreign_key() {
    DataAccessException failure = failureViolating(FOREIGN_KEY_VIOLATION, PLACEMENT_FOREIGN_KEY);

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.PLACEMENT_REFERENCE);
  }

  @Test
  void ignores_another_constraint_carrying_the_same_sqlstate() {
    // The Órgano catalogue's own unique key is a 23505 too. Matching on the SQLSTATE alone
    // would report a duplicate source key as a duplicate sibling name.
    DataAccessException failure =
        failureViolating(UNIQUE_VIOLATION, "organo_contratacion_source_key_key");

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  @Test
  void ignores_the_self_parent_check() {
    // V9's CHECK is deliberately not translated: pointing a term at itself is the cycle rule,
    // which the use cases own, not one of the two refusals this layer maps.
    DataAccessException failure = failureViolating(CHECK_VIOLATION, "termo_parent_not_self");

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  @Test
  void ignores_the_sibling_index_reported_under_another_sqlstate() {
    DataAccessException failure = failureViolating(FOREIGN_KEY_VIOLATION, SIBLING_NAME_INDEX);

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  @Test
  void finds_the_violation_deeper_in_the_cause_chain() {
    // A batched statement arrives wrapped one level further down, which is why the lookup
    // walks the causes instead of only inspecting what it was handed.
    PSQLException violation = violation(UNIQUE_VIOLATION, SIBLING_NAME_INDEX);
    DataAccessException failure = wrapping(new SQLException("batch entry failed", violation));

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.SIBLING_NAME);
  }

  @Test
  void ignores_failure_carrying_no_server_error_message() {
    // A connection that never reached the server has a SQLSTATE but no constraint to read.
    DataAccessException failure =
        wrapping(new PSQLException("connection failure", PSQLState.CONNECTION_FAILURE));

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  @Test
  void ignores_failure_that_did_not_come_from_postgres() {
    DataAccessException failure = wrapping(new SQLException("some other driver problem"));

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  @Test
  void ignores_failure_with_no_cause_at_all() {
    DataAccessException failure = new DataAccessException("no underlying SQL failure");

    TaxonomiaConstraints.Guard guard = TaxonomiaConstraints.refusedBy(failure);

    assertThat(guard).isEqualTo(TaxonomiaConstraints.Guard.NONE);
  }

  private static DataAccessException failureViolating(String sqlState, String constraintName) {
    return wrapping(violation(sqlState, constraintName));
  }

  // Micronaut Data hands the driver's SQLException up inside a DataAccessException.
  private static DataAccessException wrapping(Throwable cause) {
    return new DataAccessException("Error executing SQL", cause);
  }

  // pgjdbc parses the backend's ErrorResponse wire format, where each field is a type
  // character, its value, and a NUL. 'C' carries the SQLSTATE and 'n' the violated
  // constraint's name — the two fields the translator reads, and what lets a real
  // PSQLException be built here without a database behind it.
  private static PSQLException violation(String sqlState, String constraintName) {
    return new PSQLException(
        new ServerErrorMessage(
            "SERROR\0C%s\0Mviolates a constraint\0n%s\0".formatted(sqlState, constraintName)));
  }
}
