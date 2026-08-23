package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvClassification;
import gal.conxugal.domain.licitacion.CpvClassificationRepository;
import gal.conxugal.domain.licitacion.CpvRepository;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
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
 * {@link JdbcCpvClassificationRepository} against a real PostgreSQL, asserted over the table rather
 * than read back through the same adapter — the port offers no finder.
 *
 * <p>A classification row records that <em>this</em> procedure cites <em>that</em> entry, on that
 * date, for that award point. Two properties carry most of what follows: the row holds no code of
 * its own — the entry is the list's, and citing it means referring to it — and its lote is
 * nullable, because the source publishes a classification against the procedure as a whole even on
 * a procedure that has lotes.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCpvClassificationRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate DIFFUSED_ON = LocalDate.of(2026, 3, 14);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  CpvClassificationRepository cpvClassificationRepository;

  @Inject
  CpvRepository cpvRepository;

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
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));

    CpvClassification stored =
        cpvClassificationRepository.upsert(
            new CpvClassification(licitacionId, null, cpv, DIFFUSED_ON));

    assertThat(stored.id()).isNotNull();
    assertThat(classificationTable()).hasNumberOfRows(1);
    assertThat(classificationTable())
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("cpv_id").isEqualTo(identityOf(cpv))
            .value("diffusion_date").isEqualTo(DIFFUSED_ON)
            .value("withdrawn").isFalse();
  }

  // Procedure 822054: two lotes with two separate awards, and every CPV row published
  // procedure-wide. A model requiring a lote on a procedure that has them could not store this.
  @Test
  void stores_against_the_procedure_as_whole_though_it_has_lotes() {
    loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    loteRepository.upsert(new Lote(licitacionId, "2", null, null));
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));

    cpvClassificationRepository.upsert(new CpvClassification(licitacionId, null, cpv, DIFFUSED_ON));

    assertThat(classificationTable()).hasNumberOfRows(1);
    assertThat(classificationTable())
        .row(0)
            .value("lote_id").isNull()
            .value("licitacion_id").isEqualTo(licitacionId.value());
  }

  // A classification on a lote and one on the procedure are two facts about two award points, and
  // the entry is part of the key, so citing one code twice at two levels is two rows.
  @Test
  void one_of_lote_and_one_of_the_procedure_are_two_rows() {
    Lote lote = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));

    cpvClassificationRepository.upsert(
        new CpvClassification(licitacionId, lote.id(), cpv, DIFFUSED_ON));
    cpvClassificationRepository.upsert(new CpvClassification(licitacionId, null, cpv, DIFFUSED_ON));

    assertThat(classificationTable()).hasNumberOfRows(2);
  }

  // Two codes cited procedure-wide are two rows: the entry is part of the key, so the constraint
  // bounds a procedure to one row per entry rather than to one row.
  @Test
  void two_entries_cited_by_one_procedure_are_two_rows() {
    cpvClassificationRepository.upsert(
        new CpvClassification(
            licitacionId, null, cpvRepository.upsert(new Cpv("45000000")), DIFFUSED_ON));
    cpvClassificationRepository.upsert(
        new CpvClassification(
            licitacionId, null, cpvRepository.upsert(new Cpv("45100000")), DIFFUSED_ON));

    assertThat(classificationTable()).hasNumberOfRows(2);
  }

  // The procedure-wide row's key carries a null lote, which is where NULLS NOT DISTINCT earns its
  // place: without it this would insert afresh on every run, for every lotless procedure.
  @Test
  void re_storing_classification_of_one_award_point_refreshes_it_in_place() {
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));
    CpvClassification first =
        cpvClassificationRepository.upsert(
            new CpvClassification(licitacionId, null, cpv, DIFFUSED_ON));

    CpvClassification corrected =
        cpvClassificationRepository.upsert(
            new CpvClassification(licitacionId, null, cpv, DIFFUSED_ON.plusDays(1)));

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
    CpvClassification unstoredEntry =
        new CpvClassification(licitacionId, null, new Cpv("45000000"), DIFFUSED_ON);

    assertThatThrownBy(() -> cpvClassificationRepository.upsert(unstoredEntry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CPV entry must be stored");
    assertThat(classificationTable()).hasNumberOfRows(0);
  }

  // The identity the CPV upsert answered with, which is nullable until the database assigns it: a
  // null reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(Cpv cpv) {
    return Objects.requireNonNull(cpv.id(), "the upsert answers the stored entry with its identity")
        .value();
  }

  // Ordered on the procedure and the lote, which is the whole of the key.
  private Table classificationTable() {
    return Tables.orderedBy(dataSource, "licitacion_cpv", "licitacion_id", "lote_id");
  }
}
