package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionTramitacionType;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeRepository;
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
 * {@link JdbcLicitacionTramitacionTypeRepository} against a real PostgreSQL, on
 * {@link JdbcLicitacionContractTypeRepositoryIntegrationTest}'s shape and for its reasons.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionTramitacionTypeRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionTramitacionTypeRepository tramitacionTypeRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionTramitacionType stored =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria"));

    assertThat(tramitacionTypeTable()).hasNumberOfRows(1);
    assertThat(tramitacionTypeTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("name").isEqualTo("Ordinaria");
  }

  @Test
  void name_nobody_has_published_before_stores_rather_than_failing() {
    LicitacionTramitacionType stored =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Urxente"));

    assertThat(stored.id()).isNotNull();
    assertThat(tramitacionTypeTable()).hasNumberOfRows(1);
  }

  @Test
  void re_storing_type_the_source_has_published_before_leaves_one_row() {
    LicitacionTramitacionType first =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria"));

    LicitacionTramitacionType again =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(tramitacionTypeTable()).hasNumberOfRows(1);
  }

  @Test
  void storing_name_leaves_the_other_type_vocabularies_empty() {
    tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Aberto"));

    assertThat(tramitacionTypeTable()).hasNumberOfRows(1);
    assertThat(Tables.orderedBy(dataSource, "licitacion_contract_type", "name"))
        .hasNumberOfRows(0);
    assertThat(Tables.orderedBy(dataSource, "licitacion_procedure_type", "name"))
        .hasNumberOfRows(0);
  }

  private static UUID identityOf(LicitacionTramitacionType type) {
    return Objects.requireNonNull(type.id(), "the upsert answers the stored type with its identity")
        .value();
  }

  private Table tramitacionTypeTable() {
    return Tables.orderedBy(dataSource, "licitacion_tramitacion_type", "name");
  }
}
