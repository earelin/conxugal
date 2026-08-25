package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Award;
import gal.conxugal.domain.licitacion.AwardeeResolutionPath;
import gal.conxugal.domain.licitacion.Licitacion;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.StoreLicitacionAwards;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.db.type.AssertDbConnectionFactory;
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
 * What resolving a procedure's awardees leaves in the catalogue and in the award table, against a
 * real PostgreSQL: which route answers when the catalogue is what has to answer, that the inferring
 * route creates nothing, and which spelling an operador awarded two lotes ends up displayed under.
 *
 * <p><strong>{@code transactional = false}</strong>, on
 * {@link LicitacionBiddersDerivationIntegrationTest}'s reasoning: these are about what committed,
 * and a re-import leaving the catalogue byte for byte as it was must be true for production's
 * reason rather than for a rolled-back wrapper's.
 *
 * <p>The catalogue match is here rather than beside the routing unit tests because the fold it
 * compares on is written twice — once in {@code MatchableName} and once as SQL — and only a real
 * database can say the two agree.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionAwardeeDerivationIntegrationTest implements TestPropertyProvider {

  private static final String PUBLICATION_ID = "822054";
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2024, 7, 10);
  private static final FiscalIdentifier EQUINSE_ID = new FiscalIdentifier("A41111220");
  private static final String EQUINSE = "EQUINSE, S.A.";
  private static final String EQUINSE_SPELLED_OUT = "Equinse Sociedade Anónima";
  private static final Money AWARDED = new Money(new BigDecimal("206996.66"));

  /**
   * Every row's physical location, which PostgreSQL moves whenever a row is rewritten. It is what
   * tells a row left alone from one updated with the values it already had.
   */
  private static final String CATALOGUE_TUPLE_VERSIONS =
      """
      SELECT 'operador:' || ctid::text AS version FROM operador_economico
      UNION ALL
      SELECT 'nome:' || ctid::text AS version FROM operador_economico_nome_alternativo
      ORDER BY version
      """;

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  StoreLicitacionAwards storeAwards;

  @Inject
  LoteRepository lotes;

  @Inject
  OperadorRepository operadores;

  private Licitacion licitacion;

  @BeforeEach
  void theProcedureIsStored() throws Exception {
    licitacion = insertLicitacion();
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // Path C, end to end and against the SQL half of the fold: the catalogue holds the name shouted
  // and unpunctuated, the award publishes it accented and punctuated, and they are one name.
  @Test
  void awardee_matching_one_catalogued_operador_resolves_derived_and_creates_no_operador()
      throws Exception {
    operadores.insert(
        new OperadorEconomico(
            new FiscalIdentifier("B15112222"),
            "XESTION AMBIENTAL DE CONTRATAS SL",
            new NomeRank(PUBLISHED_ON, 2001090L)));

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, "Xestión Ambiental de Contratas, S.L.")),
            List.of(),
            List.of());

    assertThat(stored)
        .singleElement()
        .extracting(Award::awardeeResolutionPath)
        .isEqualTo(AwardeeResolutionPath.NAME_DERIVED);
    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(awardTable())
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorIdOf("B15112222"))
            .value("awardee_name").isEqualTo("Xestión Ambiental de Contratas, S.L.")
            .value("awardee_resolution_path").isEqualTo("NAME_DERIVED");
  }

  // An invented operador is what SPEC-0006 R5 forbids and no import undoes, so the promise is
  // asserted as the catalogue's contents rather than as a call that was not made.
  @Test
  void awardee_matching_nobody_stores_the_award_naming_nobody_and_catalogues_nothing()
      throws Exception {
    store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(operadorTable()).hasNumberOfRows(0);
    assertThat(awardTable()).hasNumberOfRows(1);
    assertThat(awardTable())
        .row(0)
            .value("operador_economico_id").isNull()
            .value("awardee_name").isEqualTo(EQUINSE)
            .value("awardee_resolution_path").isEqualTo("UNRESOLVED")
            .value("withdrawn").isFalse();
  }

  // Measured ambiguity is 1 name in 268 and that one a source typo — so the award names nobody
  // rather than whichever of the two the query happened to answer first.
  @Test
  void awardee_matching_two_catalogued_operadores_stores_the_award_naming_nobody() {
    operadores.insert(
        new OperadorEconomico(EQUINSE_ID, EQUINSE, new NomeRank(PUBLISHED_ON, 2001090L)));
    operadores.insert(
        new OperadorEconomico(
            new FiscalIdentifier("B15112222"), "Equinse SA", new NomeRank(PUBLISHED_ON, 2001091L)));

    List<Award> stored = store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isNull();
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.UNRESOLVED);
            });
  }

  // SPEC-0006 R4 as amended: the rank carries no lote, so two awards of one procedure tie exactly
  // and the first accounted for supplies the name. Pinned here rather than left incidental.
  @Test
  void two_lotes_awarded_to_one_operador_display_the_spelling_accounted_for_first()
      throws Exception {
    List<Lote> awardPoints = storedLotes("1", "2");
    List<PublishedAward> published =
        List.of(award("1", EQUINSE), award("2", EQUINSE_SPELLED_OUT));
    List<PublishedFormalisation> formalisations =
        List.of(
            formalisation("1", EQUINSE, EQUINSE_ID),
            formalisation("2", EQUINSE_SPELLED_OUT, EQUINSE_ID));

    store(awardPoints, published, formalisations, List.of());

    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(operadorTable())
        .row(0)
            .value("name").isEqualTo(EQUINSE);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(1);
    assertThat(nomeAlternativoTable())
        .row(0)
            .value("name").isEqualTo(EQUINSE_SPELLED_OUT);
  }

  @Test
  void reimporting_the_procedure_swaps_neither_spelling_and_rewrites_no_catalogue_row()
      throws Exception {
    List<Lote> awardPoints = storedLotes("1", "2");
    List<PublishedAward> published =
        List.of(award("1", EQUINSE), award("2", EQUINSE_SPELLED_OUT));
    List<PublishedFormalisation> formalisations =
        List.of(
            formalisation("1", EQUINSE, EQUINSE_ID),
            formalisation("2", EQUINSE_SPELLED_OUT, EQUINSE_ID));

    store(awardPoints, published, formalisations, List.of());
    List<String> afterFirstImport = catalogueTupleVersions();
    store(awardPoints, published, formalisations, List.of());

    assertThat(awardTable()).hasNumberOfRows(2);
    assertThat(catalogueTupleVersions()).isEqualTo(afterFirstImport);
  }

  // The rank-less entry point, end to end: the identifier still resolves and the award still
  // stores, and the operador it created stands behind every ranked publication.
  @Test
  void procedure_whose_publication_identifier_is_not_number_resolves_but_advances_no_name()
      throws Exception {
    Licitacion unnumbered = licitacionPublishedAs(new PublicationId("LIC-2026/0042"));

    List<Award> stored =
        storeAwards.store(
            unnumbered,
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
            List.of());

    assertThat(stored)
        .singleElement()
        .extracting(Award::awardeeResolutionPath)
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(operadorTable())
        .row(0)
            .value("name").isEqualTo(EQUINSE)
            .value("name_rank_date").isNull()
            .value("name_rank_source_id").isEqualTo(Long.MIN_VALUE);
  }

  // The other half of the same rule, and the half a rank engineered to lose would break silently:
  // an operador a contract already named keeps that name and that rank, and the award's own
  // spelling does not reach the retained set either.
  @Test
  void procedure_with_no_number_advances_no_name_of_an_operador_already_catalogued()
      throws Exception {
    operadores.insert(
        new OperadorEconomico(EQUINSE_ID, EQUINSE, new NomeRank(PUBLISHED_ON, 2001090L)));
    Licitacion unnumbered = licitacionPublishedAs(new PublicationId("LIC-2026/0042"));

    storeAwards.store(
        unnumbered,
        List.of(),
        List.of(award(null, EQUINSE_SPELLED_OUT)),
        List.of(formalisation(null, EQUINSE_SPELLED_OUT, EQUINSE_ID)),
        List.of());

    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(operadorTable())
        .row(0)
            .value("name").isEqualTo(EQUINSE)
            .value("name_rank_source_id").isEqualTo(2001090L);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(0);
  }

  private List<Award> store(
      List<Lote> awardPoints,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders) {
    return storeAwards.store(licitacion, awardPoints, published, formalisations, bidders);
  }

  private List<Lote> storedLotes(String... identifiers) {
    List<Lote> stored = new ArrayList<>(identifiers.length);
    for (String identifier : identifiers) {
      stored.add(lotes.upsert(new Lote(licitacionId(), identifier, null, null)));
    }
    return stored;
  }

  private LicitacionId licitacionId() {
    return licitacion.id();
  }

  private static PublishedAward award(@Nullable String loteKey, String awardeeName) {
    return new PublishedAward(
        loteKey, "Adxudicado", PUBLISHED_ON, AWARDED, "12 meses", awardeeName, null);
  }

  private static PublishedFormalisation formalisation(
      @Nullable String loteKey, String contratista, FiscalIdentifier fiscalIdentifier) {
    return new PublishedFormalisation(
        loteKey, PUBLISHED_ON, contratista, fiscalIdentifier, "España", AWARDED);
  }

  private static List<String> catalogueTupleVersions() throws SQLException {
    return column(CATALOGUE_TUPLE_VERSIONS, "version");
  }

  private static UUID operadorIdOf(String fiscalId) throws SQLException {
    return UUID.fromString(
        column("SELECT id::text AS id FROM operador_economico WHERE fiscal_id = ?", "id", fiscalId)
            .getFirst());
  }

  private static List<String> column(String sql, String name, Object... arguments)
      throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, arguments);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          values.add(rows.getString(name));
        }
      }
    }
    return values;
  }

  private static Table operadorTable() {
    return table("operador_economico", "fiscal_id");
  }

  private static Table nomeAlternativoTable() {
    return table("operador_economico_nome_alternativo", "name");
  }

  private static Table awardTable() {
    return table("licitacion_award", "awardee_name");
  }

  // Off the container rather than the injected DataSource: with no ambient transaction every write
  // under test really commits, and these assertions are about what it committed.
  private static Table table(String name, String order) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(order)})
        .build();
  }

  /**
   * The procedure these awards were made on, written straight rather than through the adapters:
   * what this class is about is the resolution, and the procedure beneath it is scenery.
   */
  private static Licitacion insertLicitacion() throws SQLException {
    UUID organoId =
        insertReturningId(
            "INSERT INTO organo_contratacion (id, source_key, name, active)"
                + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
            "242",
            "Axencia Turismo de Galicia");
    UUID stateId =
        insertReturningId(
            "INSERT INTO licitacion_state (code, label) VALUES ('2', 'Adxudicado') RETURNING id");
    UUID licitacionId =
        insertReturningId(
            "INSERT INTO licitacion (publication_id, organo_id, state_id, publication_date)"
                + " VALUES (?, ?, ?, ?) RETURNING id",
            PUBLICATION_ID,
            organoId,
            stateId,
            java.sql.Date.valueOf(PUBLISHED_ON));
    return new Licitacion(
        new LicitacionId(licitacionId),
        new PublicationId(PUBLICATION_ID),
        new OrganoId(organoId),
        PUBLISHED_ON,
        PUBLISHED_ON,
        new LicitacionState(new LicitacionStateId(stateId), 2, "Adxudicado"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  /** The same stored procedure, read as though the source had published it under another key. */
  private Licitacion licitacionPublishedAs(PublicationId publicationId) {
    return new Licitacion(
        licitacion.id(),
        publicationId,
        licitacion.organoId(),
        licitacion.publicationDate(),
        licitacion.lastModified(),
        licitacion.state(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  private static UUID insertReturningId(String sql, Object... arguments) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, arguments);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return rows.getObject("id", UUID.class);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
    for (int argument = 0; argument < arguments.length; argument++) {
      statement.setObject(argument + 1, arguments[argument]);
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
