package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateRepository;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link JdbcLicitacionStateRepository} against a real PostgreSQL. What the write left behind is
 * asserted over the table rather than read back through the same adapter — the port offers no
 * finder, and a read-back would only show one adapter agreeing with itself.
 *
 * <p>The state is the one vocabulary keyed on a <strong>code</strong> rather than on published
 * text, and the one that carries a second column the source can correct. Both of those are what
 * this class is about: the code is what a row is matched on, and the label is neither a key nor
 * unique.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionStateRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionStateRepository stateRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionState stored = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    assertThat(stored.code()).isEqualTo(2);
    Table states = stateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states)
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("code").isEqualTo(2)
            .value("label").isEqualTo("Adxudicado");
  }

  // Codes 101 and 102 are both published as Histórico. This is the test that would fail if the
  // label ever became the key: the store would hold one row where the source publishes two states.
  @Test
  void two_codes_sharing_one_label_are_two_states() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(101, "Histórico"));
    LicitacionState second = stateRepository.upsert(new LicitacionState(102, "Histórico"));

    assertThat(first.id()).isNotEqualTo(second.id());
    Table states = stateTable();
    assertThat(states).hasNumberOfRows(2);
    assertThat(states)
        .row(0)
            .value("code").isEqualTo(101)
            .value("label").isEqualTo("Histórico");
    assertThat(states)
        .row(1)
            .value("code").isEqualTo(102)
            .value("label").isEqualTo("Histórico");
  }

  // Code 7 was never observed and the set is not closed, so an unknown code costs a row rather
  // than a foreign-key violation and a rejected procedure.
  @Test
  void code_the_table_has_never_held_stores_rather_than_failing() {
    LicitacionState stored = stateRepository.upsert(new LicitacionState(7, null));

    assertThat(stored.id()).isNotNull();
    assertThat(stateTable()).hasNumberOfRows(1);
    assertThat(stateTable())
        .row(0)
            .value("code").isEqualTo(7)
            .value("label").isNull();
  }

  // A run over thousands of procedures naming one state must not grow the vocabulary.
  @Test
  void re_storing_state_the_source_has_published_before_leaves_one_row() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    LicitacionState again = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(stateTable()).hasNumberOfRows(1);
  }

  // The label is the one thing about a state the source can correct, and the code is what the row
  // is matched on, so the correction lands on the row already stored.
  @Test
  void re_storing_state_under_corrected_label_refreshes_it_in_place() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    stateRepository.upsert(new LicitacionState(2, "Adxudicado definitivamente"));

    assertThat(stateTable()).hasNumberOfRows(1);
    assertThat(stateTable())
        .row(0)
            .value("id").isEqualTo(identityOf(first))
            .value("label").isEqualTo("Adxudicado definitivamente");
  }

  // The identity the upsert answered with, which is nullable until the database assigns it: a null
  // reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(LicitacionState state) {
    return Objects.requireNonNull(
            state.id(), "the upsert answers the stored state with its identity")
        .value();
  }

  // Ordered on the published key so row(n) is stable, rather than leaning on uuidv7 insertion
  // order.
  private Table stateTable() {
    return Tables.orderedBy(dataSource, "licitacion_state", "code");
  }
}
