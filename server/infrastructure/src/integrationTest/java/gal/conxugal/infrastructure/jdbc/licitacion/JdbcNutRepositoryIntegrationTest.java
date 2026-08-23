package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutRepository;
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
 * {@link JdbcNutRepository} against a real PostgreSQL, on
 * {@link JdbcCpvRepositoryIntegrationTest}'s shape and for its reasons: the two regulated lists are
 * byte-identical but for the table and the constraint they name, so each is asked the same three
 * questions of its own — including the one that matters most, that an upsert declines to write the
 * description.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcNutRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  NutRepository nutRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void code_the_list_has_never_held_stores_with_no_description() {
    Nut stored = nutRepository.upsert(new Nut("ES111"));

    assertThat(stored.id()).isNotNull();
    assertThat(nutTable()).hasNumberOfRows(1);
    assertThat(nutTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("code").isEqualTo("ES111")
            .value("description").isNull();
  }

  @Test
  void upsert_leaves_the_stored_description_alone() {
    nutRepository.upsert(new Nut(null, "ES111", "A Coruña"));

    Nut again = nutRepository.upsert(new Nut("ES111"));

    assertThat(again.description()).isEqualTo("A Coruña");
    assertThat(nutTable()).hasNumberOfRows(1);
    assertThat(nutTable())
        .row(0)
            .value("code").isEqualTo("ES111")
            .value("description").isEqualTo("A Coruña");
  }

  @Test
  void re_storing_entry_the_list_already_holds_leaves_one_row() {
    Nut first = nutRepository.upsert(new Nut("ES111"));

    Nut again = nutRepository.upsert(new Nut("ES111"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(nutTable()).hasNumberOfRows(1);
  }

  private static UUID identityOf(Nut nut) {
    return Objects.requireNonNull(nut.id(), "the upsert answers the stored entry with its identity")
        .value();
  }

  private Table nutTable() {
    return Tables.orderedBy(dataSource, "nut", "code");
  }
}
