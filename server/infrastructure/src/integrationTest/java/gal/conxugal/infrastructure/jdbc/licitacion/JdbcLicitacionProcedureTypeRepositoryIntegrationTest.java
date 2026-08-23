package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionProcedureType;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeRepository;
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
 * {@link JdbcLicitacionProcedureTypeRepository} against a real PostgreSQL, on
 * {@link JdbcLicitacionContractTypeRepositoryIntegrationTest}'s shape and for its reasons: the
 * three type adapters are byte-identical but for the table and the constraint they name, so each is
 * asked the same four questions of its own.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionProcedureTypeRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionProcedureTypeRepository procedureTypeRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionProcedureType stored =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    assertThat(procedureTypeTable()).hasNumberOfRows(1);
    assertThat(procedureTypeTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("name").isEqualTo("Aberto");
  }

  @Test
  void name_nobody_has_published_before_stores_rather_than_failing() {
    LicitacionProcedureType stored =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Diálogo competitivo"));

    assertThat(stored.id()).isNotNull();
    assertThat(procedureTypeTable()).hasNumberOfRows(1);
  }

  @Test
  void re_storing_type_the_source_has_published_before_leaves_one_row() {
    LicitacionProcedureType first =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    LicitacionProcedureType again =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(procedureTypeTable()).hasNumberOfRows(1);
  }

  @Test
  void storing_name_leaves_the_other_type_vocabularies_empty() {
    procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    assertThat(procedureTypeTable()).hasNumberOfRows(1);
    assertThat(Tables.orderedBy(dataSource, "licitacion_contract_type", "name"))
        .hasNumberOfRows(0);
    assertThat(Tables.orderedBy(dataSource, "licitacion_tramitacion_type", "name"))
        .hasNumberOfRows(0);
  }

  private static UUID identityOf(LicitacionProcedureType type) {
    return Objects.requireNonNull(type.id(), "the upsert answers the stored type with its identity")
        .value();
  }

  private Table procedureTypeTable() {
    return Tables.orderedBy(dataSource, "licitacion_procedure_type", "name");
  }
}
