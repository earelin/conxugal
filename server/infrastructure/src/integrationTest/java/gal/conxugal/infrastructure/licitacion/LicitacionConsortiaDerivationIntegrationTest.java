package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Award;
import gal.conxugal.domain.licitacion.ConsortiumOperadores;
import gal.conxugal.domain.licitacion.Licitacion;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedConsortiumMember;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.StoreLicitacionAwards;
import gal.conxugal.domain.licitacion.StoreLicitacionConsortia;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
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
 * What cataloguing a procedure's consortia leaves in the catalogue, against a real PostgreSQL: one
 * operador per consortium in either branch, its members beside it, the membership between them, and
 * the award held by the consortium and by no member of it.
 *
 * <p>These belong against a real database rather than beside the unit tests because most of what
 * they assert is what the <em>catalogue holds</em> — an identifier-less row coexisting with an
 * identified one under a {@code UNIQUE} that treats nulls as distinct, a re-import finding the row
 * its own bid named through a fold written in SQL, and no award row pointing at a member.
 *
 * <p><strong>{@code transactional = false}</strong>, on
 * {@link LicitacionAwardeeDerivationIntegrationTest}'s reasoning: a re-import minting no second
 * consortium has to be true for production's reason rather than for a rolled-back wrapper's.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionConsortiaDerivationIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2024, 7, 10);
  private static final String UTE = "UTE PRACE-TABOADA RAMOS";
  private static final FiscalIdentifier UTE_ID = new FiscalIdentifier("U88779475");
  private static final FiscalIdentifier PRACE_ID = new FiscalIdentifier("A70319678");
  private static final FiscalIdentifier TABOADA_ID = new FiscalIdentifier("B94181807");
  private static final String PRACE = "PRACE SERVICIOS Y OBRAS SA";
  private static final String TABOADA = "CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU";
  private static final Money AWARDED = new Money(new BigDecimal("206996.66"));

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  StoreLicitacionConsortia storeConsortia;

  @Inject
  StoreLicitacionAwards storeAwards;

  @Inject
  OperadorRepository operadores;

  @Inject
  UteMembershipRepository memberships;

  private Licitacion licitacion;

  @BeforeEach
  void theProcedureIsStored() throws Exception {
    licitacion = insertLicitacion("822054");
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // The 2-of-35 branch, whole: the consortium is an ordinary catalogue entry carrying the marker,
  // its members are catalogued under their own identifiers, the membership is stored, and the
  // award it won is held by it.
  @Test
  void consortium_publishing_its_identifier_is_catalogued_with_its_members_and_holds_its_award()
      throws Exception {
    ConsortiumOperadores catalogued = catalogueConsortium(licitacion, UTE, UTE_ID);

    List<Award> awards = storeAwards(List.of(award(null, UTE)), List.of(), catalogued);

    UUID ute = operadorIdOf(UTE_ID);
    assertThat(operadorTable()).hasNumberOfRows(3);
    assertThat(nameOf(UTE_ID)).isEqualTo(UTE);
    assertThat(isMarkedAsUte(UTE_ID)).isTrue();
    assertThat(membersOf(ute))
        .containsExactlyInAnyOrder(operadorIdOf(PRACE_ID), operadorIdOf(TABOADA_ID));
    assertThat(awards)
        .singleElement()
        .extracting(Award::operadorEconomicoId)
        .isEqualTo(new OperadorId(ute));
  }

  // The 33-of-35 branch, whole. SPEC-0008 #20 as amended: a consortium is not the party R16
  // removes, so it is catalogued holding no identifier and its award names it rather than nobody.
  @Test
  void consortium_publishing_no_identifier_is_catalogued_holding_none_and_holds_its_award()
      throws Exception {
    ConsortiumOperadores catalogued = catalogueConsortium(licitacion, UTE, null);

    List<Award> awards = storeAwards(List.of(award(null, UTE)), List.of(), catalogued);

    assertThat(operadorTable()).hasNumberOfRows(3);
    assertThat(identifierLessOperadores()).containsExactly(UTE);
    assertThat(membershipTable()).hasNumberOfRows(2);
    assertThat(awards)
        .singleElement()
        .extracting(Award::operadorEconomicoId)
        .isEqualTo(catalogued.at(matchable(UTE)).operadorId());
    assertThat(awardTable())
        .row(0)
            .value("operador_economico_id")
                .isEqualTo(catalogued.at(matchable(UTE)).operadorId().value())
            .value("awardee_resolution_path").isEqualTo("PUBLISHED_BY_BIDDER");
  }

  // SPEC-0008 #17. The row the store offers no key to be found by: without the procedure-scoped
  // lookup a re-import mints a second consortium and leaves the previous bid visible beside it.
  @Test
  void reimporting_the_procedure_mints_no_second_consortium_and_no_second_bid() throws Exception {
    ConsortiumOperadores first = catalogueConsortium(licitacion, UTE, null);

    ConsortiumOperadores second = catalogueConsortium(licitacion, UTE, null);

    assertThat(second.at(matchable(UTE)).operadorId())
        .isEqualTo(first.at(matchable(UTE)).operadorId());
    assertThat(identifierLessOperadores()).hasSize(1);
    assertThat(participationTable()).hasNumberOfRows(1);
    assertThat(membershipTable()).hasNumberOfRows(2);
  }

  // SPEC-0006 #40: what the unidentified branch gives up is continuity, and the system claims none
  // the source did not publish. Two bids a reader would call one consortium are two operadores.
  @Test
  void two_procedures_publishing_one_name_produce_two_operadores() throws Exception {
    Licitacion other = insertLicitacion("828959");

    ConsortiumOperadores here = catalogueConsortium(licitacion, UTE, null);
    ConsortiumOperadores there = catalogueConsortium(other, UTE, null);

    assertThat(there.at(matchable(UTE)).operadorId())
        .isNotEqualTo(here.at(matchable(UTE)).operadorId());
    assertThat(identifierLessOperadores()).hasSize(2);
  }

  // SPEC-0006 #40, and the shape that ordering exists to rule out: identified is a property of the
  // procedure, so a formalisation publishing what the bidder row did not leaves ONE operador —
  // never an identifier-less bid beside an identified award.
  @Test
  void consortium_only_the_formalisation_identifies_leaves_one_operador_for_bid_and_award()
      throws Exception {
    List<PublishedFormalisation> formalisations = List.of(formalisation(null, UTE, UTE_ID));

    ConsortiumOperadores catalogued =
        storeConsortia.store(
            licitacion.id(),
            List.of(),
            List.of(consortium(null, UTE, null, member(PRACE, PRACE_ID))),
            formalisations);
    List<Award> awards = storeAwards(List.of(award(null, UTE)), formalisations, catalogued);

    assertThat(identifierLessOperadores()).isEmpty();
    UUID ute = operadorIdOf(UTE_ID);
    assertThat(participationTable())
        .row(0)
            .value("operador_economico_id").isEqualTo(ute);
    assertThat(awards)
        .singleElement()
        .extracting(Award::operadorEconomicoId)
        .isEqualTo(new OperadorId(ute));
  }

  // The storage precondition for SPEC-0008 #22: an award to the consortium enters no member's
  // history, so no member can count a euro its consortium was paid.
  @Test
  void no_award_beside_either_kind_of_consortium_points_at_one_of_its_members() throws Exception {
    Licitacion other = insertLicitacion("828959");

    ConsortiumOperadores unidentified = catalogueConsortium(licitacion, UTE, null);
    storeAwards(List.of(award(null, UTE)), List.of(), unidentified);
    ConsortiumOperadores identified = catalogueConsortium(other, UTE, UTE_ID);
    storeAwards(other, List.of(award(null, UTE)), List.of(), identified);

    assertThat(awardedOperadores())
        .doesNotContain(operadorIdOf(PRACE_ID), operadorIdOf(TABOADA_ID))
        .containsExactlyInAnyOrder(
            unidentified.at(matchable(UTE)).operadorId().value(),
            identified.at(matchable(UTE)).operadorId().value());
  }

  // SPEC-0008 #20 reaching a member: R16's rule removes the party it cannot identify, and the
  // consortium, its other members and the procedure are all unaffected.
  @Test
  void member_whose_identifier_is_unusable_yields_no_operador_and_no_membership() throws Exception {
    ConsortiumOperadores catalogued =
        storeConsortia.store(
            licitacion.id(),
            List.of(),
            List.of(
                consortium(
                    null, UTE, null, member("Sen NIF SL", null), member(TABOADA, TABOADA_ID))),
            List.of());

    assertThat(operadorTable()).hasNumberOfRows(2);
    assertThat(membersOf(catalogued.at(matchable(UTE)).operadorId().value()))
        .containsExactly(operadorIdOf(TABOADA_ID));
    assertThat(participationTable()).hasNumberOfRows(1);
  }

  // SPEC-0006 #40 as amended: contratos menores import ahead of licitacións, so a UTE holding an
  // ordinary identifier is often already catalogued as a plain firm. It is marked, not duplicated.
  @Test
  void consortium_that_contrato_menor_catalogued_first_is_marked_rather_than_left_unmarked()
      throws Exception {
    operadores.insert(
        new OperadorEconomico(UTE_ID, "UTE PRACE TABOADA", new NomeRank(PUBLISHED_ON, 2001090L)));

    catalogueConsortium(licitacion, UTE, UTE_ID);

    assertThat(operadorTable()).hasNumberOfRows(3);
    assertThat(isMarkedAsUte(UTE_ID)).isTrue();
    assertThat(nameOf(UTE_ID)).isEqualTo("UTE PRACE TABOADA");
  }

  // One shape, two branches: the membership rows an identified consortium produces are the ones an
  // unidentified one produces, which is what lets a member firm read them from its own end.
  @Test
  void identified_and_unidentified_consortium_produce_the_same_membership_rows() throws Exception {
    Licitacion other = insertLicitacion("828959");

    ConsortiumOperadores identified = catalogueConsortium(licitacion, UTE, UTE_ID);
    ConsortiumOperadores unidentified = catalogueConsortium(other, UTE, null);

    assertThat(membersOf(identified.at(matchable(UTE)).operadorId().value()))
        .containsExactlyInAnyOrderElementsOf(
            membersOf(unidentified.at(matchable(UTE)).operadorId().value()));
    OperadorId prace = new OperadorId(operadorIdOf(PRACE_ID));
    assertThat(memberships.findByOperadorIdAndWithdrawnFalse(prace))
        .extracting(UteMembership::uteId)
        .containsExactlyInAnyOrder(
            identified.at(matchable(UTE)).operadorId(),
            unidentified.at(matchable(UTE)).operadorId());
  }

  /** One consortium and its two member firms, catalogued and bid on the procedure as a whole. */
  private ConsortiumOperadores catalogueConsortium(
      Licitacion procedure, String name, @Nullable FiscalIdentifier fiscalIdentifier) {
    return storeConsortia.store(
        procedure.id(),
        List.of(),
        List.of(
            consortium(
                null,
                name,
                fiscalIdentifier,
                member(PRACE, PRACE_ID),
                member(TABOADA, TABOADA_ID))),
        List.of());
  }

  private List<Award> storeAwards(
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      ConsortiumOperadores consortia) {
    return storeAwards(licitacion, published, formalisations, consortia);
  }

  private List<Award> storeAwards(
      Licitacion procedure,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      ConsortiumOperadores consortia) {
    List<Lote> lotes = List.of();
    return storeAwards.store(procedure, lotes, published, formalisations, List.of(), consortia);
  }

  private static MatchableName matchable(String publishedName) {
    return MatchableName.of(publishedName).orElseThrow();
  }

  private static PublishedConsortiumMember member(
      String name, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedConsortiumMember(name, fiscalIdentifier);
  }

  private static PublishedBidder consortium(
      @Nullable String loteKey,
      String name,
      @Nullable FiscalIdentifier fiscalIdentifier,
      PublishedConsortiumMember... members) {
    return new PublishedBidder.Consortium(loteKey, name, fiscalIdentifier, List.of(members));
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

  /** The names of every catalogue entry holding no fiscal identifier, which is every minted UTE. */
  private static List<String> identifierLessOperadores() throws SQLException {
    return column(
        "SELECT name FROM operador_economico WHERE fiscal_id IS NULL ORDER BY name", "name");
  }

  private static List<UUID> membersOf(UUID uteId) throws SQLException {
    return idColumn(
        "SELECT operador_economico_id::text AS id FROM operador_ute_membership WHERE ute_id = ?",
        uteId);
  }

  private static List<UUID> awardedOperadores() throws SQLException {
    return idColumn(
        "SELECT operador_economico_id::text AS id FROM licitacion_award"
            + " WHERE operador_economico_id IS NOT NULL");
  }

  private static String nameOf(FiscalIdentifier fiscalId) throws SQLException {
    return column("SELECT name FROM operador_economico WHERE fiscal_id = ?", "name",
            fiscalId.value())
        .getFirst();
  }

  private static boolean isMarkedAsUte(FiscalIdentifier fiscalId) throws SQLException {
    return Boolean.parseBoolean(
        column("SELECT ute::text AS ute FROM operador_economico WHERE fiscal_id = ?", "ute",
                fiscalId.value())
            .getFirst());
  }

  private static UUID operadorIdOf(FiscalIdentifier fiscalId) throws SQLException {
    return idColumn(
            "SELECT id::text AS id FROM operador_economico WHERE fiscal_id = ?", fiscalId.value())
        .getFirst();
  }

  private static List<UUID> idColumn(String sql, Object... arguments) throws SQLException {
    return column(sql, "id", arguments).stream().map(UUID::fromString).toList();
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
    return table("operador_economico", "name");
  }

  private static Table membershipTable() {
    return table("operador_ute_membership", "operador_economico_id");
  }

  private static Table participationTable() {
    return table("licitacion_participation", "id");
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
   * The procedure these bids were made on, written straight rather than through the adapters: what
   * this class is about is the cataloguing, and the procedure beneath it is scenery.
   */
  private static Licitacion insertLicitacion(String publicationId) throws SQLException {
    UUID organoId =
        insertReturningId(
            "INSERT INTO organo_contratacion (id, source_key, name, active)"
                + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
            publicationId,
            "Axencia Turismo de Galicia");
    UUID stateId =
        insertReturningId(
            "INSERT INTO licitacion_state (code, label) VALUES (2, 'Adxudicado')"
                + " ON CONFLICT ON CONSTRAINT licitacion_state_code_key"
                + " DO UPDATE SET label = EXCLUDED.label RETURNING id");
    UUID licitacionId =
        insertReturningId(
            "INSERT INTO licitacion (publication_id, organo_id, state_id, publication_date)"
                + " VALUES (?, ?, ?, ?) RETURNING id",
            publicationId,
            organoId,
            stateId,
            java.sql.Date.valueOf(PUBLISHED_ON));
    return new Licitacion(
        new LicitacionId(licitacionId),
        new PublicationId(publicationId),
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
