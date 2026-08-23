package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionContractType;
import gal.conxugal.domain.licitacion.LicitacionContractTypeRepository;
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
 * {@link JdbcLicitacionContractTypeRepository} against a real PostgreSQL, asserted over the table
 * rather than read back through the same adapter — the port offers no finder.
 *
 * <p>This adapter and the two beside it are byte-identical but for the table and the constraint
 * they name, which is exactly why each is driven into its own conflict branch and each is asked to
 * leave the other two tables alone. A copy naming the wrong one would otherwise go unnoticed.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionContractTypeRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionContractTypeRepository contractTypeRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionContractType stored =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    assertThat(contractTypeTable()).hasNumberOfRows(1);
    assertThat(contractTypeTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("name").isEqualTo("Servizos");
  }

  // Nothing seeds this vocabulary and nothing validates against it, so a name the source has never
  // published costs a row rather than a rejected procedure.
  @Test
  void name_nobody_has_published_before_stores_rather_than_failing() {
    LicitacionContractType stored =
        contractTypeRepository.upsert(new LicitacionContractType("Concesión de obras públicas"));

    assertThat(stored.id()).isNotNull();
    assertThat(contractTypeTable()).hasNumberOfRows(1);
  }

  // A run over thousands of procedures naming one type must not grow the vocabulary.
  @Test
  void re_storing_type_the_source_has_published_before_leaves_one_row() {
    LicitacionContractType first =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    LicitacionContractType again =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(contractTypeTable()).hasNumberOfRows(1);
  }

  // The three type vocabularies are separately keyed, so one published name meaning three different
  // things is three entries in three tables — and this adapter writes exactly one of them.
  @Test
  void storing_name_leaves_the_other_type_vocabularies_empty() {
    contractTypeRepository.upsert(new LicitacionContractType("Aberto"));

    assertThat(contractTypeTable()).hasNumberOfRows(1);
    assertThat(Tables.orderedBy(dataSource, "licitacion_procedure_type", "name"))
        .hasNumberOfRows(0);
    assertThat(Tables.orderedBy(dataSource, "licitacion_tramitacion_type", "name"))
        .hasNumberOfRows(0);
  }

  // The identity the upsert answered with, which is nullable until the database assigns it: a null
  // reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(LicitacionContractType type) {
    return Objects.requireNonNull(type.id(), "the upsert answers the stored type with its identity")
        .value();
  }

  // Ordered on the published key so row(n) is stable, rather than leaning on uuidv7 insertion
  // order.
  private Table contractTypeTable() {
    return Tables.orderedBy(dataSource, "licitacion_contract_type", "name");
  }
}
