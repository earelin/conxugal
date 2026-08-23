package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Formalisation;
import gal.conxugal.domain.licitacion.FormalisationRepository;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link JdbcFormalisationRepository} against a real PostgreSQL, asserted over the table rather
 * than read back through the same adapter — the port offers no finder.
 *
 * <p>Its own table and its own adapter rather than columns on the award, because the two are
 * separate publications that can disagree: of 284 measured award rows, 112 sat on procedures in a
 * state that has no formalisation at all, and where both exist they can name different parties.
 *
 * <p>{@code fiscal_identifier} is what this adapter holds that nothing else does: the identifier a
 * single {@code Contratista} cell carried, which may identify nobody the catalogue holds and is
 * absent whenever the cell's trailing token was not identifier-shaped.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFormalisationRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate FORMALISED_ON = LocalDate.of(2026, 3, 14);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  FormalisationRepository formalisationRepository;

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
  void upsert_stores_the_identifier_the_contratista_cell_carried() {
    Formalisation stored =
        formalisationRepository.upsert(formalisationOf(null, "EQUINSE, S.A.", "A41111220"));

    assertThat(stored.id()).isNotNull();
    assertThat(formalisationTable()).hasNumberOfRows(1);
    assertThat(formalisationTable())
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("lote_id").isNull()
            .value("formalisation_date").isEqualTo(FORMALISED_ON)
            .value("contratista_name").isEqualTo("EQUINSE, S.A.")
            .value("fiscal_identifier").isEqualTo("A41111220")
            .value("nationality").isEqualTo("España")
            .value("amount").isEqualTo(new BigDecimal("206996.66"))
            .value("withdrawn").isFalse();
  }

  // The cell whose trailing token was not identifier-shaped carried none, and the column is
  // nullable for exactly that: this row is not the catalogue's key and need resolve to nobody.
  @Test
  void stores_with_no_fiscal_identifier() {
    Formalisation stored = formalisationRepository.upsert(formalisationOf(null, "AQUAGEST", null));

    assertThat(stored.id()).isNotNull();
    assertThat(formalisationTable()).hasNumberOfRows(1);
    assertThat(formalisationTable())
        .row(0)
            .value("contratista_name").isEqualTo("AQUAGEST")
            .value("fiscal_identifier").isNull()
            .value("nationality").isEqualTo("España");
  }

  // A formalisation belongs to exactly one award point, so a procedure with lotes formalises each
  // of them separately and a lotless one formalises once, against the procedure.
  @Test
  void one_of_lote_and_one_of_the_procedure_are_two_award_points() {
    Lote lote = loteRepository.upsert(new Lote(licitacionId, "1", null, null));

    formalisationRepository.upsert(formalisationOf(lote.id(), "EQUINSE, S.A.", "A41111220"));
    formalisationRepository.upsert(formalisationOf(null, "AQUAGEST", null));

    assertThat(formalisationTable()).hasNumberOfRows(2);
  }

  // The procedure-wide row's key carries a null lote, which is where NULLS NOT DISTINCT earns its
  // place: without it this would insert afresh on every run, for every lotless procedure.
  @Test
  void re_storing_formalisation_of_one_award_point_refreshes_it_in_place() {
    Formalisation first =
        formalisationRepository.upsert(formalisationOf(null, "EQUINSE, S.A.", "A41111220"));

    Formalisation corrected =
        formalisationRepository.upsert(formalisationOf(null, "AQUAGEST", null));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(formalisationTable()).hasNumberOfRows(1);
    assertThat(formalisationTable())
        .row(0)
            .value("contratista_name").isEqualTo("AQUAGEST")
            .value("fiscal_identifier").isNull();
  }

  // Every other idempotence case here keys on a null lote. This is the ordinary half: a
  // formalisation of a lote, whose key has no null in it at all.
  @Test
  void re_storing_formalisation_of_one_lote_refreshes_it_in_place() {
    Lote lote = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Formalisation first =
        formalisationRepository.upsert(formalisationOf(lote.id(), "EQUINSE, S.A.", "A41111220"));

    Formalisation corrected =
        formalisationRepository.upsert(formalisationOf(lote.id(), "EQUINSE, S.L.", "A41111220"));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(formalisationTable()).hasNumberOfRows(1);
    assertThat(formalisationTable())
        .row(0)
            .value("lote_id").isEqualTo(identityOf(lote))
            .value("contratista_name").isEqualTo("EQUINSE, S.L.");
  }

  private Formalisation formalisationOf(
      @Nullable LoteId loteId, String contratista, @Nullable String fiscalIdentifier) {
    return new Formalisation(
        licitacionId,
        loteId,
        FORMALISED_ON,
        contratista,
        fiscalIdentifier == null ? null : new FiscalIdentifier(fiscalIdentifier),
        "España",
        new Money(new BigDecimal("206996.66")));
  }

  private static UUID identityOf(Lote lote) {
    return Objects.requireNonNull(lote.id(), "the upsert answers the stored lote with its identity")
        .value();
  }

  // Ordered on the procedure and the lote, which is the whole of the key.
  private Table formalisationTable() {
    return Tables.orderedBy(dataSource, "licitacion_formalisation", "licitacion_id", "lote_id");
  }
}
