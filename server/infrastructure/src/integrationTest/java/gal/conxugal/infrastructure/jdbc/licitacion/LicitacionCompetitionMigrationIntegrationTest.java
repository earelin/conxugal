package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;
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
 * V21's guarantees for the two competition tables and the catalogue change they rest on, verified
 * directly rather than through an adapter: most of the claims are about what the schema refuses,
 * and the ones that matter most are about what it deliberately does <em>not</em> — a consortium
 * catalogued with no fiscal identifier, and a second one beside it.
 *
 * <p>Driven with raw SQL and committed, on {@link LicitacionMigrationIntegrationTest}'s reasoning:
 * a deliberately violated constraint aborts the connection Micronaut Data shares with the adapters.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionCompetitionMigrationIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  DataSource dataSource;

  private SchemaFixture schema;
  private UUID licitacionId;

  @BeforeEach
  void setUp() throws Exception {
    schema = SchemaFixture.committing(dataSource);
    UUID organoId = schema.insertOrgano("consorcio-x");
    UUID stateId = schema.insertState(2, "Adxudicado");
    licitacionId = schema.insertLicitacion("822054", organoId, stateId);
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // No consortium marker and no published name: a consortium is an operador now, so this row says
  // who bid by reference and holds no description of the party at all.
  @Test
  void the_participation_holds_the_bid_and_no_description_of_who_made_it() throws Exception {
    assertThat(schema.columnNamesOf("licitacion_participation"))
        .containsExactlyInAnyOrder(
            "id", "licitacion_id", "lote_id", "operador_economico_id", "won", "withdrawn");
  }

  // No surrogate id, and that absence is the point: the pair is the identity, so an id beside it
  // would be a second key naming the same row -- and it is the pair a repeated member collapses on.
  @Test
  void the_membership_holds_its_two_references_and_takes_no_identity_of_its_own() throws Exception {
    assertThat(schema.columnNamesOf("operador_ute_membership"))
        .containsExactlyInAnyOrder("ute_id", "operador_economico_id", "withdrawn");
  }

  @Test
  void the_catalogue_gained_the_one_kind_it_records() throws Exception {
    assertThat(schema.columnNamesOf("operador_economico"))
        .containsExactlyInAnyOrder(
            "id", "fiscal_id", "name", "ute", "name_rank_date", "name_rank_source_id");
  }

  // 33 of 35 measured consortia publish no identifier. The catalogue holds them under the bid that
  // published them, which is what the column becoming nullable is for.
  @Test
  void consortium_catalogued_with_no_fiscal_identifier_is_accepted() {
    assertThatCode(() -> schema.insertUnidentifiedUte("UTE PRACE-TABOADA RAMOS"))
        .doesNotThrowAnyException();

    assertThat(operadores()).hasNumberOfRows(1);
    assertThat(operadores())
        .row(0)
            .value("fiscal_id").isNull()
            .value("ute").isTrue()
            .value("name").isEqualTo("UTE PRACE-TABOADA RAMOS");
  }

  // The reason the UNIQUE must stay NULLS DISTINCT: declared NULLS NOT DISTINCT it would collapse
  // every unidentified consortium onto one row, pooling unrelated firms under one identity.
  @Test
  void two_consortia_catalogued_with_no_fiscal_identifier_stay_two_operadores() throws Exception {
    schema.insertUnidentifiedUte("UTE PRACE-TABOADA RAMOS");
    schema.insertUnidentifiedUte("UTE PRACE-TABOADA RAMOS");

    assertThat(operadores()).hasNumberOfRows(2);
  }

  // Making the column nullable did not weaken it: a real identifier is still one operador.
  @Test
  void second_operador_under_stored_fiscal_identifier_is_still_refused() throws Exception {
    schema.insertOperadorEconomico("A41111220", "EQUINSE, S.A.");

    assertThatThrownBy(() -> schema.insertOperadorEconomico("A41111220", "Outro nome"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesUniqueness(exception, "operador_economico_fiscal_id_key"));
  }

  // The identifier is optional for a consortium and for nothing else. Without this the column
  // would read as optional for everybody, and a resolution defect would catalogue an ordinary firm
  // the catalogue could never find again.
  @Test
  void ordinary_operador_carrying_no_fiscal_identifier_is_refused() {
    assertThatThrownBy(() -> schema.insertOperadorWithoutFiscalId("EQUINSE, S.A."))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesCheck(exception, "operador_economico_fiscal_id_check"));
  }

  @Test
  void second_bid_by_one_operador_on_one_lote_is_refused_though_its_lote_is_null()
      throws Exception {
    UUID operadorId = schema.insertOperadorEconomico("A41111220", "EQUINSE, S.A.");
    schema.insertParticipation(licitacionId, null, operadorId);

    assertThatThrownBy(() -> schema.insertParticipation(licitacionId, null, operadorId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_participation_key"));
  }

  // The lotless procedure whose bidder resolved to nobody: two of three key components null, which
  // is where NULLS NOT DISTINCT earns its place on this table.
  @Test
  void second_unresolved_bid_on_one_procedure_is_refused_though_two_key_parts_are_null()
      throws Exception {
    schema.insertParticipation(licitacionId, null, null);

    assertThatThrownBy(() -> schema.insertParticipation(licitacionId, null, null))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_participation_key"));
  }

  @Test
  void second_membership_of_one_member_under_one_consortium_is_refused_by_the_primary_key()
      throws Exception {
    UUID uteId = schema.insertUnidentifiedUte("UTE PRACE-TABOADA RAMOS");
    UUID memberId = schema.insertOperadorEconomico("A41111220", "EQUINSE, S.A.");
    schema.insertUteMembership(uteId, memberId);

    assertThatThrownBy(() -> schema.insertUteMembership(uteId, memberId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "operador_ute_membership_pkey"));
  }

  @Test
  void membership_of_unknown_consortium_is_refused_by_the_foreign_key() throws Exception {
    UUID memberId = schema.insertOperadorEconomico("A41111220", "EQUINSE, S.A.");

    assertThatThrownBy(() -> schema.insertUteMembership(UUID.randomUUID(), memberId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesForeignKey(exception, "operador_ute_membership_ute_id_fkey"));
  }

  @Test
  void the_participation_reaches_its_procedure_its_lote_and_the_operador_catalogue()
      throws Exception {
    assertThat(schema.foreignKeyTargetsOf("licitacion_participation"))
        .containsExactlyInAnyOrder("licitacion", "licitacion_lote", "operador_economico");
  }

  // Both ends are catalogue entries and no licitación appears in it, which is what lets the
  // relation be read from either side rather than only from the member's.
  @Test
  void the_membership_reaches_the_catalogue_at_both_ends_and_nothing_else() throws Exception {
    assertThat(schema.foreignKeyTargetsOf("operador_ute_membership"))
        .containsExactly("operador_economico", "operador_economico");
  }

  // licitacion_id is already leftmost in the natural key's index, so it is not indexed again.
  @Test
  void the_participation_is_indexed_on_its_key_its_lote_and_its_operador_and_nothing_else()
      throws Exception {
    assertThat(schema.indexNamesOf("licitacion_participation"))
        .containsExactlyInAnyOrder(
            "licitacion_participation_pkey",
            "licitacion_participation_key",
            "licitacion_participation_lote_id_idx",
            "licitacion_participation_operador_economico_id_idx");
  }

  // ute_id is leftmost in the primary key, so only the member's end needs an index of its own —
  // and it needs one, because reading the relation from that end is half its purpose.
  @Test
  void the_membership_is_indexed_on_its_key_and_on_the_member_end_and_nothing_else()
      throws Exception {
    assertThat(schema.indexNamesOf("operador_ute_membership"))
        .containsExactlyInAnyOrder(
            "operador_ute_membership_pkey",
            "operador_ute_membership_operador_economico_id_idx");
  }

  private Table operadores() {
    return Tables.orderedBy(dataSource, "operador_economico", "name", "id");
  }
}
