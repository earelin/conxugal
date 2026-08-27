package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.licitacion.Participation;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.StoreLicitacionBidders;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.ResolveOperador;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What a procedure's bidders leave in the catalogue, against a real PostgreSQL: which bids create
 * an operador, which leave every catalogued one exactly as they found it, and what the source's own
 * captured bidder table produces once it has been through the parse.
 *
 * <p><strong>{@code transactional = false}</strong>, because these are about what committed: a
 * re-import must leave the catalogue byte for byte as it was, which the default's rolled-back
 * wrapper would make true for a reason production does not have.
 *
 * <p>It sits in this package rather than beside the JDBC adapters so it can drive the record parse
 * on the page the source really served — the guarantee about consortia is about the catalogue's
 * contents after an import, and a hand-built bidder list would be asserting against the fixture
 * this task chose rather than against the one the source publishes.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionBiddersDerivationIntegrationTest implements TestPropertyProvider {

  private static final String PUBLICATION_ID = "822054";
  private static final FiscalIdentifier EQUINSE_ID = new FiscalIdentifier("A41111220");
  private static final String EQUINSE = "EQUINSE, S.A.";
  private static final LocalDate JANUARY = LocalDate.of(2019, 1, 10);
  private static final NomeRank CONTRACT_RANK = new NomeRank(JANUARY, 2001090L);

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
  StoreLicitacionBidders storeBidders;

  @Inject
  ResolveOperador resolveOperador;

  @Inject
  LoteRepository lotes;

  @Inject
  OperadorRepository operadores;

  private LicitacionId licitacionId;

  @BeforeEach
  void theProcedureIsStored() throws Exception {
    licitacionId = insertLicitacion();
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // R16 needs the bid recorded and R3 needs its identifier to resolve to something, so this much a
  // bid does decide. It is the only thing about the catalogue that it decides.
  @Test
  void bid_naming_firm_no_contract_named_catalogues_it_under_the_published_name() {
    storeBidders.store(licitacionId, List.of(), List.of(singleFirm(EQUINSE, EQUINSE_ID)));

    Table catalogue = operadorTable();
    assertThat(catalogue).hasNumberOfRows(1);
    assertThat(catalogue)
        .row(0)
            .value("fiscal_id").isEqualTo(EQUINSE_ID.value())
            .value("name").isEqualTo(EQUINSE)
            .value("ute").isFalse();
    assertThat(nomeAlternativoTable()).hasNumberOfRows(0);
  }

  // The rule an earlier draft of the task inverted. R4 selects from the operador's most recently
  // published *contract*, and a bid is not one — so it cannot take the display however late it is,
  // and it does not join the retained set on the way past either.
  @Test
  void losing_bid_neither_renames_catalogued_operador_nor_joins_its_retained_names() {
    operadores.insert(new OperadorEconomico(EQUINSE_ID, EQUINSE, new NomeRank(JANUARY, 100L)));

    storeBidders.store(
        licitacionId, List.of(), List.of(singleFirm("Equinse Sociedade Anónima", EQUINSE_ID)));

    Table catalogue = operadorTable();
    assertThat(catalogue).hasNumberOfRows(1);
    assertThat(catalogue)
        .row(0)
            .value("name").isEqualTo(EQUINSE)
            .value("name_rank_source_id").isEqualTo(100L);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(0);
  }

  @Test
  void reimporting_the_procedure_leaves_one_bid_per_published_bidder_and_flaps_no_name()
      throws Exception {
    List<PublishedBidder> published = capturedBidders();
    List<Lote> awardPoints = storedLotes("1", "2");

    storeBidders.store(licitacionId, awardPoints, published);
    List<String> afterFirstImport = catalogueTupleVersions();
    storeBidders.store(licitacionId, awardPoints, published);

    assertThat(participationTable()).hasNumberOfRows(16);
    assertThat(catalogueTupleVersions()).isEqualTo(afterFirstImport);
  }

  // The observable form of the guarantee: not that a call did not happen, but that no consortium
  // and no placeholder reached the catalogue by importing the bidder table the source published.
  // Its one consortium row carries a literal dash where the identifier would be.
  @Test
  void captured_bidder_table_catalogues_its_firms_and_neither_consortium_nor_placeholder()
      throws Exception {
    storeBidders.store(licitacionId, storedLotes("1", "2"), capturedBidders());

    assertThat(operadorTable()).hasNumberOfRows(14);
    assertThat(participationTable()).hasNumberOfRows(16);
    assertThat(catalogueNames()).doesNotContain("UTE PRACE-TABOADA RAMOS");
    assertThat(catalogueIdentifiers())
        .doesNotContain("-")
        .noneMatch(fiscalId -> fiscalId.startsWith("TEMP-"))
        .doesNotHaveDuplicates();
  }

  // The 578-of-613 exception, end to end: the bid is stored naming nobody rather than dropped, and
  // nothing is catalogued to stand in for the party.
  @Test
  void bidder_whose_identifier_is_unusable_stores_bid_naming_nobody_and_catalogues_none()
      throws Exception {
    List<Participation> stored =
        storeBidders.store(
            licitacionId,
            List.of(),
            List.of(singleFirm(EQUINSE, EQUINSE_ID), singleFirm("Sen NIF SL", null)));

    assertThat(stored).hasSize(2);
    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(participationTable()).hasNumberOfRows(2);
    assertThat(storedParties()).containsExactlyInAnyOrder(operadorIdOf(EQUINSE_ID), null);
  }

  // The other half of the same rule, end to end: the bid's name goes when the first contract to
  // name the operador displaces it, rather than being filed among the alternatives R15 fills from
  // that operador's contracts.
  @Test
  void contract_displacing_the_name_only_the_bid_published_leaves_no_retained_name()
      throws Exception {
    storeBidders.store(licitacionId, List.of(), List.of(singleFirm(EQUINSE, EQUINSE_ID)));

    resolveOperador.resolve(EQUINSE_ID.value(), "Equinse Sociedade Anónima", CONTRACT_RANK);

    Table catalogue = operadorTable();
    assertThat(catalogue).hasNumberOfRows(1);
    assertThat(catalogue)
        .row(0)
            .value("name").isEqualTo("Equinse Sociedade Anónima")
            .value("name_rank_source_id").isEqualTo(2001090L);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(0);
  }

  private List<Lote> storedLotes(String... identifiers) {
    List<Lote> stored = new ArrayList<>(identifiers.length);
    for (String identifier : identifiers) {
      stored.add(lotes.upsert(new Lote(licitacionId, identifier, null, null)));
    }
    return stored;
  }

  /** The bidder table of the page the source really served, as the parse hands it over. */
  private static List<PublishedBidder> capturedBidders() {
    LicitacionRecord record =
        LicitacionRecordDocument.read(new PublicationId(PUBLICATION_ID), capture());
    return record.bidders();
  }

  private static Document capture() {
    String name = "/licitacion/%s.html".formatted(PUBLICATION_ID);
    Class<?> here = LicitacionBiddersDerivationIntegrationTest.class;
    try (InputStream page = here.getResourceAsStream(name)) {
      String absent = "Captured record %s is not on the classpath".formatted(name);
      return Jsoup.parse(
          Objects.requireNonNull(page, absent), StandardCharsets.ISO_8859_1.name(), "/licitacion");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static PublishedBidder singleFirm(
      String name, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedBidder.SingleFirm(null, name, fiscalIdentifier);
  }

  /** The party each stored bid names, nulls included — which is the value under test here. */
  private static List<String> storedParties() throws SQLException {
    return column(
        "SELECT operador_economico_id::text AS party FROM licitacion_participation", "party");
  }

  private static String operadorIdOf(FiscalIdentifier fiscalId) throws SQLException {
    return column(
            "SELECT id::text AS id FROM operador_economico WHERE fiscal_id = ?",
            "id",
            fiscalId.value())
        .getFirst();
  }

  private static List<String> catalogueNames() throws SQLException {
    return column("SELECT name FROM operador_economico ORDER BY name", "name");
  }

  private static List<String> catalogueIdentifiers() throws SQLException {
    return column("SELECT fiscal_id FROM operador_economico ORDER BY fiscal_id", "fiscal_id");
  }

  private static List<String> catalogueTupleVersions() throws SQLException {
    return column(CATALOGUE_TUPLE_VERSIONS, "version");
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

  private static Table participationTable() {
    return table("licitacion_participation", "operador_economico_id");
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
   * The procedure these bids were made for, written straight rather than through the adapters:
   * what this class is about is the competition, and the procedure beneath it is scenery.
   */
  private static LicitacionId insertLicitacion() throws SQLException {
    UUID organoId =
        insertReturningId(
            "INSERT INTO organo_contratacion (id, source_key, name, active)"
                + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
            "242",
            "Axencia Turismo de Galicia");
    UUID stateId =
        insertReturningId(
            "INSERT INTO licitacion_state (code, label) VALUES ('2', 'Adxudicado') RETURNING id");
    return new LicitacionId(
        insertReturningId(
            "INSERT INTO licitacion (publication_id, organo_id, state_id) VALUES (?, ?, ?)"
                + " RETURNING id",
            PUBLICATION_ID,
            organoId,
            stateId));
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
