package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutClassification;
import gal.conxugal.domain.licitacion.NutClassificationRepository;
import gal.conxugal.domain.licitacion.NutRepository;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 * {@link JdbcNutClassificationRepository} against a real PostgreSQL, on
 * {@link JdbcCpvClassificationRepositoryIntegrationTest}'s shape and for its reasons: the two
 * classification adapters are byte-identical but for the table, the constraint and the list they
 * cite, so each is asked the same questions of its own. A copy naming the wrong one would otherwise
 * go unnoticed.
 *
 * <p>The NUT table was measured procedure-wide on 217 of 240 procedures, so the nullable lote is
 * the ordinary case here rather than the exception.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcNutClassificationRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate DIFFUSED_ON = LocalDate.of(2026, 3, 14);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  NutClassificationRepository nutClassificationRepository;

  @Inject
  NutRepository nutRepository;

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
  void upsert_records_the_procedure_the_entry_and_the_date_it_was_diffused() {
    Nut nut = nutRepository.upsert(new Nut("ES111"));

    NutClassification stored =
        nutClassificationRepository.upsert(
            new NutClassification(licitacionId, null, nut, DIFFUSED_ON));

    assertThat(stored.id()).isNotNull();
    assertThat(classificationTable()).hasNumberOfRows(1);
    assertThat(classificationTable())
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("nut_id").isEqualTo(identityOf(nut))
            .value("diffusion_date").isEqualTo(DIFFUSED_ON)
            .value("withdrawn").isFalse();
  }

  @Test
  void stores_against_the_procedure_as_whole_though_it_has_lotes() {
    loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    loteRepository.upsert(new Lote(licitacionId, "2", null, null));
    Nut nut = nutRepository.upsert(new Nut("ES111"));

    nutClassificationRepository.upsert(new NutClassification(licitacionId, null, nut, DIFFUSED_ON));

    assertThat(classificationTable()).hasNumberOfRows(1);
    assertThat(classificationTable())
        .row(0)
            .value("lote_id").isNull()
            .value("licitacion_id").isEqualTo(licitacionId.value());
  }

  @Test
  void one_of_lote_and_one_of_the_procedure_are_two_rows() {
    Lote lote = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Nut nut = nutRepository.upsert(new Nut("ES111"));

    nutClassificationRepository.upsert(
        new NutClassification(licitacionId, lote.id(), nut, DIFFUSED_ON));
    nutClassificationRepository.upsert(new NutClassification(licitacionId, null, nut, DIFFUSED_ON));

    assertThat(classificationTable()).hasNumberOfRows(2);
  }

  // The procedure-wide row's key carries a null lote, which is where NULLS NOT DISTINCT earns its
  // place: without it this would insert afresh on every run, for every lotless procedure.
  @Test
  void re_storing_classification_of_one_award_point_refreshes_it_in_place() {
    Nut nut = nutRepository.upsert(new Nut("ES111"));
    NutClassification first =
        nutClassificationRepository.upsert(
            new NutClassification(licitacionId, null, nut, DIFFUSED_ON));

    NutClassification corrected =
        nutClassificationRepository.upsert(
            new NutClassification(licitacionId, null, nut, DIFFUSED_ON.plusDays(1)));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(classificationTable()).hasNumberOfRows(1);
    assertThat(classificationTable())
        .row(0)
            .value("diffusion_date").isEqualTo(DIFFUSED_ON.plusDays(1));
  }

  // The entry is the list's, and one the database has never assigned an identity to cannot be
  // referenced — so the mistake is named here rather than reported as a null in a NOT NULL column.
  @Test
  void citing_entry_that_carries_no_identity_is_refused() {
    NutClassification unstoredEntry =
        new NutClassification(licitacionId, null, new Nut("ES111"), DIFFUSED_ON);

    assertThatThrownBy(() -> nutClassificationRepository.upsert(unstoredEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NUTS entry must be stored");
    assertThat(classificationTable()).hasNumberOfRows(0);
  }

  private static UUID identityOf(Nut nut) {
    return Objects.requireNonNull(nut.id(), "the upsert answers the stored entry with its identity")
        .value();
  }

  private Table classificationTable() {
    return Tables.orderedBy(dataSource, "licitacion_nut", "licitacion_id", "lote_id");
  }
}
