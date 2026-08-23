package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Award;
import gal.conxugal.domain.licitacion.AwardRepository;
import gal.conxugal.domain.licitacion.AwardeeResolutionPath;
import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvClassification;
import gal.conxugal.domain.licitacion.CpvClassificationRepository;
import gal.conxugal.domain.licitacion.CpvRepository;
import gal.conxugal.domain.licitacion.Formalisation;
import gal.conxugal.domain.licitacion.FormalisationRepository;
import gal.conxugal.domain.licitacion.Licitacion;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.LicitacionRepository;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateRepository;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutClassification;
import gal.conxugal.domain.licitacion.NutClassificationRepository;
import gal.conxugal.domain.licitacion.NutRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
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
 * {@link JdbcAwardRepository} against a real PostgreSQL, and the three claims that belong to no
 * single child adapter: the <strong>lotless</strong> procedure — 85 of 100 measured, and the
 * ordinary one — whose award, formalisation and classifications all hang off the procedure itself
 * with a null lote; that storing all four of them twice leaves one of each, which is where
 * {@code NULLS NOT DISTINCT} earns its place; and that every child is born visible.
 *
 * <p>Those three are asserted here because each spans four or five adapters at once, and a class
 * named after one of them would be asserting mostly about the others. What each adapter does on its
 * own lives in its own class — {@link JdbcLoteRepositoryIntegrationTest},
 * {@link JdbcCpvClassificationRepositoryIntegrationTest},
 * {@link JdbcNutClassificationRepositoryIntegrationTest} and
 * {@link JdbcFormalisationRepositoryIntegrationTest}.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAwardPointRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionRepository licitacionRepository;

  @Inject
  LicitacionStateRepository stateRepository;

  @Inject
  LoteRepository loteRepository;

  @Inject
  CpvRepository cpvRepository;

  @Inject
  NutRepository nutRepository;

  @Inject
  CpvClassificationRepository cpvClassificationRepository;

  @Inject
  NutClassificationRepository nutClassificationRepository;

  @Inject
  AwardRepository awardRepository;

  @Inject
  FormalisationRepository formalisationRepository;

  @Inject
  DataSource dataSource;

  private SchemaFixture catalogue;
  private LicitacionId licitacionId;

  @BeforeEach
  void setUp() throws Exception {
    catalogue = SchemaFixture.joiningTheTestTransaction(dataSource);
    licitacionId = storedProcedure("822054");
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // The ordinary case, and the one an earlier draft of the schema could not express: with only a
  // nullable lote to hang from, a lotless procedure had nowhere to put any of these three.
  @Test
  void lotless_procedure_stores_its_award_its_formalisation_and_its_cpv_row() {
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));

    awardRepository.upsert(awardOf(null, "Adxudicado", null));
    formalisationRepository.upsert(formalisationOf(null, "EQUINSE, S.A.", "A41111220"));
    cpvClassificationRepository.upsert(
        new CpvClassification(licitacionId, null, cpv, PUBLISHED_ON));

    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_award"))
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("lote_id").isNull();
    assertThat(table("licitacion_formalisation")).hasNumberOfRows(1);
    assertThat(table("licitacion_formalisation"))
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("lote_id").isNull();
    assertThat(table("licitacion_cpv")).hasNumberOfRows(1);
    assertThat(table("licitacion_cpv"))
        .row(0)
            .value("licitacion_id").isEqualTo(licitacionId.value())
            .value("lote_id").isNull()
            .value("cpv_id").isEqualTo(identityOf(cpv));
  }

  // Every child's key has a null component here, which is exactly where NULLS NOT DISTINCT earns
  // its place: without it each of these would insert afresh on every run, for every procedure.
  @Test
  void storing_the_children_of_lotless_procedure_twice_leaves_one_of_each() {
    Cpv cpv = cpvRepository.upsert(new Cpv("45000000"));
    Nut nut = nutRepository.upsert(new Nut("ES111"));

    for (int run = 0; run < 2; run++) {
      awardRepository.upsert(awardOf(null, "Adxudicado", null));
      formalisationRepository.upsert(formalisationOf(null, "EQUINSE, S.A.", "A41111220"));
      cpvClassificationRepository.upsert(
          new CpvClassification(licitacionId, null, cpv, PUBLISHED_ON));
      nutClassificationRepository.upsert(
          new NutClassification(licitacionId, null, nut, PUBLISHED_ON));
    }

    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_formalisation")).hasNumberOfRows(1);
    assertThat(table("licitacion_cpv")).hasNumberOfRows(1);
    assertThat(table("licitacion_nut")).hasNumberOfRows(1);
  }

  // R16 and R25 make an award that names nobody a supported outcome rather than a failure, and
  // the resolution path beside it is what says so rather than a null being read as one.
  @Test
  void award_stores_with_no_operador_at_all() {
    Award stored = awardRepository.upsert(awardOf(null, "Adxudicado", null));

    assertThat(stored.id()).isNotNull();
    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_award"))
        .row(0)
            .value("operador_economico_id").isNull()
            .value("awardee_name").isEqualTo("EQUINSE, S.A.")
            .value("awardee_resolution_path").isEqualTo("UNRESOLVED");
  }

  @Test
  void award_stores_with_the_operador_it_was_resolved_to() throws Exception {
    OperadorId operadorId = catalogue.operador("A41111220", "EQUINSE, S.A.");

    awardRepository.upsert(
        new Award(
            licitacionId,
            null,
            "Adxudicado",
            PUBLISHED_ON,
            new Money(new BigDecimal("206996.66")),
            "2 meses 7 días",
            "EQUINSE, S.A.",
            operadorId,
            AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION));

    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_award"))
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorId.value())
            .value("amount").isEqualTo(new BigDecimal("206996.66"))
            .value("execution_period").isEqualTo("2 meses 7 días")
            .value("awardee_resolution_path").isEqualTo("PUBLISHED_BY_FORMALISATION");
  }

  // The regression test for the one-place rule the parse enforces: two lotes means two awards and
  // no third copy of either standing for the procedure.
  @Test
  void procedure_with_two_lotes_stores_two_awards_and_none_at_procedure_level() throws Exception {
    Lote first = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Lote second = loteRepository.upsert(new Lote(licitacionId, "2", null, null));

    awardRepository.upsert(awardOf(first.id(), "Adxudicado", null));
    awardRepository.upsert(awardOf(second.id(), "Adxudicado", null));

    assertThat(table("licitacion_award")).hasNumberOfRows(2);
    assertThat(procedureLevelAwards()).isZero();
  }

  // Every other idempotence test here keys on a null lote, which is the case NULLS NOT DISTINCT
  // exists for. This is the ordinary half: a child of a lote, whose key has no null in it at all.
  @Test
  void re_storing_award_of_one_lote_refreshes_it_in_place() {
    Lote lote = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Award first = awardRepository.upsert(awardOf(lote.id(), "Adxudicado", null));

    Award corrected =
        awardRepository.upsert(awardOf(lote.id(), "Adxudicado", new BigDecimal("999.99")));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_award"))
        .row(0)
            .value("lote_id").isEqualTo(identityOf(lote))
            .value("amount").isEqualTo(new BigDecimal("999.99"));
  }

  @Test
  void re_storing_award_of_one_award_point_refreshes_it_in_place() {
    Award first = awardRepository.upsert(awardOf(null, "Adxudicado", null));

    Award corrected =
        awardRepository.upsert(awardOf(null, "Adxudicado", new BigDecimal("999.99")));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(table("licitacion_award")).hasNumberOfRows(1);
    assertThat(table("licitacion_award"))
        .row(0)
            .value("amount").isEqualTo(new BigDecimal("999.99"));
  }

  @Test
  void every_child_is_stored_visible() {
    loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    awardRepository.upsert(awardOf(null, "Adxudicado", null));
    formalisationRepository.upsert(formalisationOf(null, "AQUAGEST", null));
    cpvClassificationRepository.upsert(
        new CpvClassification(
            licitacionId, null, cpvRepository.upsert(new Cpv("45000000")), PUBLISHED_ON));
    nutClassificationRepository.upsert(
        new NutClassification(
            licitacionId, null, nutRepository.upsert(new Nut("ES111")), PUBLISHED_ON));

    assertThat(loteTable()).hasNumberOfRows(1);
    assertThat(loteTable()).row(0).value("withdrawn").isFalse();
    for (String child :
        List.of(
            "licitacion_award", "licitacion_formalisation", "licitacion_cpv", "licitacion_nut")) {
      assertThat(table(child)).hasNumberOfRows(1);
      assertThat(table(child)).row(0).value("withdrawn").isFalse();
    }
  }

  // The identity the CPV upsert answered with, which is nullable until the database assigns it: a
  // null reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(Lote lote) {
    return Objects.requireNonNull(lote.id(), "the upsert answers the stored lote with its identity")
        .value();
  }

  private static UUID identityOf(Cpv cpv) {
    return Objects.requireNonNull(cpv.id(), "the upsert answers the stored entry with its identity")
        .value();
  }

  private Award awardOf(
      @Nullable LoteId loteId, String resolution, @Nullable BigDecimal amount) {
    return new Award(
        licitacionId,
        loteId,
        resolution,
        PUBLISHED_ON,
        amount == null ? null : new Money(amount),
        "12 meses",
        "EQUINSE, S.A.",
        null,
        AwardeeResolutionPath.UNRESOLVED);
  }

  private Formalisation formalisationOf(
      @Nullable LoteId loteId, String contratista, @Nullable String fiscalIdentifier) {
    return new Formalisation(
        licitacionId,
        loteId,
        PUBLISHED_ON,
        contratista,
        fiscalIdentifier == null ? null : new FiscalIdentifier(fiscalIdentifier),
        "España",
        new Money(new BigDecimal("206996.66")));
  }

  private LicitacionId storedProcedure(String publicationId) throws SQLException {
    OrganoId organoId = catalogue.organo("consorcio-%s".formatted(publicationId));
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    return licitacionRepository
        .upsert(
            new Licitacion(
                new PublicationId(publicationId),
                organoId,
                PUBLISHED_ON,
                PUBLISHED_ON,
                state,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null))
        .id();
  }

  /**
   * Asked in SQL rather than through a port, because no port offers it: the invariant is the
   * parse's to keep and this is its regression test, not a read anything in production makes.
   */
  private long procedureLevelAwards() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT count(*) FROM licitacion_award"
                    + " WHERE licitacion_id = ? AND lote_id IS NULL")) {
      statement.setObject(1, licitacionId.value());
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getLong(1);
      }
    }
  }

  // The lote has no lote_id of its own, so it orders on the form everything else matches it by.
  private Table loteTable() {
    return Tables.orderedBy(dataSource, "licitacion_lote", "lote_key");
  }

  // Every other child orders on the procedure and the lote, which is the whole of its key.
  private Table table(String name) {
    return Tables.orderedBy(dataSource, name, "licitacion_id", "lote_id");
  }
}
