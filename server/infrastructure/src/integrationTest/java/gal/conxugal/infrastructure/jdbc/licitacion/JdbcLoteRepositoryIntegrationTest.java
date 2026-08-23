package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.money.Money;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link JdbcLoteRepository} against a real PostgreSQL, asserted over the table rather than read
 * back through the same adapter — the port offers no finder.
 *
 * <p>The lote is the one child whose key is not the column it is stored under. {@code
 * lote_identifier} holds what the source printed and {@code lote_key} holds that identifier
 * reduced, and almost everything below is about the two staying in the right relation: matched on
 * the reduction, shown as published, and never split in two by a respelling.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLoteRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LoteRepository loteRepository;

  @Inject
  DataSource dataSource;

  private LicitacionId licitacionId;

  @BeforeEach
  void setUp() throws Exception {
    licitacionId = SchemaFixture.joiningTheTestTransaction(dataSource).licitacion("822054");
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_an_award_point_hangs_off() {
    Lote stored =
        loteRepository.upsert(
            new Lote(licitacionId, "1", "Subministración", new Money(new BigDecimal("1234.50"))));

    assertThat(stored.id()).isNotNull();
    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable())
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("lote_identifier").isEqualTo("1")
            .value("lote_key").isEqualTo("1")
            .value("description").isEqualTo("Subministración")
            .value("estimated_value").isEqualTo(new BigDecimal("1234.50"))
            .value("withdrawn").isFalse();
  }

  // OU0028, LU4001 and CO0642 are all real lote identifiers, so an integer column would have
  // rejected a real procedure.
  @Test
  void stores_under_the_identifier_that_is_not_number() {
    Lote stored = loteRepository.upsert(new Lote(licitacionId, "OU0028", null, null));

    assertThat(stored.id()).isNotNull();
    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable())
        .row(0)
            .value("lote_identifier").isEqualTo("OU0028")
            .value("lote_key").isEqualTo("OU0028");
  }

  // Stored as published: the reduction is for comparison and never for storage, so "05" that a
  // reader is shown is the "05" the source printed.
  @Test
  void stores_under_the_padded_identifier_the_source_printed() {
    loteRepository.upsert(new Lote(licitacionId, "05", "Subministración", null));

    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable())
        .row(0)
            .value("lote_identifier").isEqualTo("05")
            .value("lote_key").isEqualTo("5")
            .value("description").isEqualTo("Subministración");
  }

  // The award table produced both "1" and "05" in one measured sample, so one procedure can spell
  // one lote two ways. Keyed on the published spelling this would be two lotes, and every award
  // and bidder count hanging off them would be split between the halves.
  @Test
  void procedure_spelling_one_lote_padded_and_bare_stores_one_lote() {
    Lote padded = loteRepository.upsert(new Lote(licitacionId, "01", null, null));

    Lote bare = loteRepository.upsert(new Lote(licitacionId, "1", null, null));

    assertThat(bare.id()).isEqualTo(padded.id());
    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable())
        .row(0)
            .value("lote_identifier").isEqualTo("01")
            .value("lote_key").isEqualTo("1");
  }

  // The lote keeps the spelling it is stored under, and the answer carries that rather than what
  // was passed in. Otherwise what a reader is shown would depend on which of the record's two
  // tables its caller happened to read first, and would flip between re-imports.
  @Test
  void re_storing_under_another_spelling_keeps_the_one_already_stored() {
    loteRepository.upsert(new Lote(licitacionId, "01", null, null));

    Lote answered = loteRepository.upsert(new Lote(licitacionId, "1", "Subministración", null));

    assertThat(answered.identifier()).isEqualTo("01");
    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable())
        .row(0)
            .value("lote_identifier").isEqualTo("01")
            .value("description").isEqualTo("Subministración");
  }

  // "_" and "-" are how the source's tables spell the procedure as a whole; a row standing for
  // that hangs off the procedure with no lote at all, so storing one as a lote is a caller's slip.
  @Test
  void identifier_that_names_the_procedure_as_whole_is_refused() {
    Lote notLote = new Lote(licitacionId, "_", null, null);

    assertThatThrownBy(() -> loteRepository.upsert(notLote))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names the procedure as a whole");
    assertThat(loteTable()).hasNumberOfRows(0);
  }

  @Test
  void two_lotes_of_one_procedure_are_two_lotes() {
    loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    loteRepository.upsert(new Lote(licitacionId, "2", null, null));

    assertThat(loteTable()).hasNumberOfRows(2);
  }

  // The lote has no lote_id of its own, so it orders on the form everything else matches it by.
  private Table loteTable() {
    return Tables.orderedBy(dataSource, "licitacion_lote", "lote_key");
  }
}
