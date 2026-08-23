package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvRepository;
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
 * {@link JdbcCpvRepository} against a real PostgreSQL, asserted over the table rather than read
 * back through the same adapter — the port offers no finder.
 *
 * <p>CPV is <strong>versioned rather than closed</strong>: the 2008 revision retired codes the 2003
 * one issued, and this system imports procedures published across both, so nothing seeds the list.
 * The description is the property that makes this adapter differ from the four licitación
 * vocabularies — it is the one column an upsert must decline to write.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCpvRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  CpvRepository cpvRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void code_the_list_has_never_held_stores_with_no_description() {
    Cpv stored = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(stored.id()).isNotNull();
    assertThat(cpvTable()).hasNumberOfRows(1);
    assertThat(cpvTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("code").isEqualTo("45000000")
            .value("description").isNull();
  }

  // The record's CPV table publishes the code alone, so every import carries no wording at all. An
  // upsert that wrote it would empty a description something else had supplied, on the next run
  // over any procedure citing that code — which is why the answer carries the stored wording rather
  // than the wording it was handed.
  @Test
  void upsert_leaves_the_stored_description_alone() {
    cpvRepository.upsert(new Cpv(null, "45000000", "Traballos de construción"));

    Cpv again = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(again.description()).isEqualTo("Traballos de construción");
    assertThat(cpvTable()).hasNumberOfRows(1);
    assertThat(cpvTable())
        .row(0)
            .value("description").isEqualTo("Traballos de construción");
  }

  // A run over thousands of procedures citing one code must not grow the list.
  @Test
  void re_storing_entry_the_list_already_holds_leaves_one_row() {
    Cpv first = cpvRepository.upsert(new Cpv("45000000"));

    Cpv again = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(cpvTable()).hasNumberOfRows(1);
  }

  // The identity the upsert answered with, which is nullable until the database assigns it: a null
  // reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(Cpv cpv) {
    return Objects.requireNonNull(cpv.id(), "the upsert answers the stored entry with its identity")
        .value();
  }

  private Table cpvTable() {
    return Tables.orderedBy(dataSource, "cpv", "code");
  }
}
