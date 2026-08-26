package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
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
 * V19's guarantees for the five tables that hang off a procedure. The one the whole schema turns on
 * is {@code NULLS NOT DISTINCT}: PostgreSQL treats NULLs as distinct by default, so without it the
 * procedure-wide row of a lotless procedure — the ordinary case, 85 of 100 measured — would insert
 * afresh on every re-import, which is every procedure on every run. That cannot be shown through
 * the adapters, whose upserts absorb a repeated key by design, so it is driven with raw SQL here.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionAwardPointMigrationIntegrationTest implements TestPropertyProvider {

  private static final List<String> CHILD_TABLES =
      List.of(
          "licitacion_lote",
          "licitacion_cpv",
          "licitacion_nut",
          "licitacion_award",
          "licitacion_formalisation");

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

  // lote_key beside lote_identifier is the answer to a hazard the published spelling alone cannot
  // meet: the award table produced both "1" and "05" in one measured sample, so keying on what the
  // source printed would split one lote in two.
  @Test
  void the_lote_holds_the_published_spelling_and_the_form_everything_matches_on() throws Exception {
    assertThat(schema.columnNamesOf("licitacion_lote"))
        .containsExactlyInAnyOrder(
            "id",
            "licitacion_id",
            "lote_identifier",
            "lote_key",
            "description",
            "estimated_value",
            "withdrawn");
  }

  // The cited code is not a column here: the entry is the list's, held once and referred to.
  @Test
  void the_classification_records_the_entry_it_cites_and_holds_no_code_of_its_own()
      throws Exception {
    assertThat(schema.columnNamesOf("licitacion_cpv"))
        .containsExactlyInAnyOrder(
            "id", "licitacion_id", "lote_id", "cpv_id", "diffusion_date", "withdrawn");
    assertThat(schema.columnNamesOf("licitacion_nut"))
        .containsExactlyInAnyOrder(
            "id", "licitacion_id", "lote_id", "nut_id", "diffusion_date", "withdrawn");
  }

  @Test
  void the_award_holds_the_resolution_the_awardee_and_how_that_awardee_was_reached()
      throws Exception {
    assertThat(schema.columnNamesOf("licitacion_award"))
        .containsExactlyInAnyOrder(
            "id",
            "licitacion_id",
            "lote_id",
            "resolution",
            "resolution_date",
            "amount",
            "execution_period",
            "awardee_name",
            "operador_economico_id",
            "awardee_resolution_path",
            "withdrawn");
  }

  // fiscal_identifier rather than fiscal_id: operador_economico's column is the catalogue's key,
  // this one is what a single formalisation cell carried and may identify nobody the catalogue
  // holds.
  @Test
  void the_formalisation_holds_the_identifier_its_cell_carried() throws Exception {
    assertThat(schema.columnNamesOf("licitacion_formalisation"))
        .containsExactlyInAnyOrder(
            "id",
            "licitacion_id",
            "lote_id",
            "formalisation_date",
            "contratista_name",
            "fiscal_identifier",
            "nationality",
            "amount",
            "withdrawn");
  }

  @Test
  void every_child_is_born_visible() throws Exception {
    schema.insertLote(licitacionId, "05", "5");
    schema.insertAward(licitacionId, null);
    schema.insertFormalisation(licitacionId, null);
    schema.insertCpvClassification(licitacionId, null, schema.insertCpv("45000000", null));
    schema.insertNutClassification(licitacionId, null, schema.insertNut("ES111", null));

    for (String child : CHILD_TABLES) {
      assertThat(table(child)).hasNumberOfRows(1);
      assertThat(table(child)).row(0).value("withdrawn").isFalse();
    }
  }

  // Without NULLS NOT DISTINCT this insert would succeed, and a lotless procedure would gain a
  // second award on every re-import for ever.
  @Test
  void second_procedure_wide_award_is_refused_though_its_lote_is_null() throws Exception {
    schema.insertAward(licitacionId, null);

    assertThatThrownBy(() -> schema.insertAward(licitacionId, null))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_award_key"));
  }

  @Test
  void second_procedure_wide_formalisation_is_refused_though_its_lote_is_null() throws Exception {
    schema.insertFormalisation(licitacionId, null);

    assertThatThrownBy(() -> schema.insertFormalisation(licitacionId, null))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_formalisation_key"));
  }

  @Test
  void second_procedure_wide_classification_of_one_entry_is_refused() throws Exception {
    UUID cpvId = schema.insertCpv("45000000", null);
    schema.insertCpvClassification(licitacionId, null, cpvId);

    assertThatThrownBy(() -> schema.insertCpvClassification(licitacionId, null, cpvId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_cpv_key"));
  }

  // Two codes cited procedure-wide are two rows: the entry is part of the key, so the constraint
  // bounds a procedure to one row per entry rather than to one row.
  @Test
  void two_entries_cited_procedure_wide_are_two_classifications() throws Exception {
    schema.insertCpvClassification(licitacionId, null, schema.insertCpv("45000000", null));
    schema.insertCpvClassification(licitacionId, null, schema.insertCpv("45100000", null));

    assertThat(table("licitacion_cpv")).hasNumberOfRows(2);
  }

  // The key bounds each award point to one award; it does not exclude a procedure-level row from a
  // procedure that has lotes. Keeping the two apart is the parse's job, not the schema's.
  @Test
  void award_on_lote_and_one_on_the_procedure_are_two_award_points() throws Exception {
    UUID loteId = schema.insertLote(licitacionId, "1", "1");

    schema.insertAward(licitacionId, loteId);
    schema.insertAward(licitacionId, null);

    assertThat(table("licitacion_award")).hasNumberOfRows(2);
  }

  @Test
  void second_lote_under_one_key_is_refused() throws Exception {
    schema.insertLote(licitacionId, "05", "5");

    assertThatThrownBy(() -> schema.insertLote(licitacionId, "5", "5"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_lote_key"));
  }

  @Test
  void the_same_lote_key_under_another_procedure_is_another_lote() throws Exception {
    UUID organoId = schema.insertOrgano("axencia-y");
    UUID stateId = schema.insertState(3, "Formalizado");
    UUID other = schema.insertLicitacion("822055", organoId, stateId);
    schema.insertLote(licitacionId, "1", "1");

    schema.insertLote(other, "1", "1");

    assertThat(table("licitacion_lote")).hasNumberOfRows(2);
  }

  // UNRESOLVED is the value an award nothing resolved carries, so the absence of a link is stated
  // rather than inferred from a null beside a null.
  @Test
  void award_carrying_no_resolution_path_is_refused() {
    assertThatThrownBy(() -> schema.insertAwardWithoutResolutionPath(licitacionId))
        .isInstanceOfSatisfying(
            SQLException.class,
            Refusals::violatesNotNull);
  }

  // Every child carries its procedure, which is what lets a lotless procedure attach its rows.
  @Test
  void child_carrying_no_procedure_is_refused() {
    assertThatThrownBy(schema::insertAwardWithoutProcedure)
        .isInstanceOfSatisfying(
            SQLException.class,
            Refusals::violatesNotNull);
  }

  // The record refuses both shapes, and this is the guarantee behind it: one row it cannot be built
  // from would make its whole procedure unreadable through the read a later import performs, with
  // no delete on the port to recover it.
  @Test
  void award_naming_nobody_by_route_that_answered_is_refused() {
    assertThatThrownBy(() -> schema.insertAwardResolvedToNobody(licitacionId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesCheck(exception, "licitacion_award_awardee_resolution_check"));
  }

  @Test
  void unresolved_award_naming_an_operador_is_refused() throws Exception {
    OperadorId operadorId = schema.operador("A41111220", "EQUINSE, S.A.");

    assertThatThrownBy(() -> schema.insertUnresolvedAwardNaming(licitacionId, operadorId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesCheck(exception, "licitacion_award_awardee_resolution_check"));
  }

  @Test
  void award_on_unknown_lote_is_refused_by_the_foreign_key() {
    assertThatThrownBy(() -> schema.insertAward(licitacionId, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesForeignKey(exception, "licitacion_award_lote_id_fkey"));
  }

  @Test
  void classification_citing_unknown_entry_is_refused_by_the_foreign_key() {
    assertThatThrownBy(
            () -> schema.insertCpvClassification(licitacionId, null, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesForeignKey(exception, "licitacion_cpv_cpv_id_fkey"));
  }

  @Test
  void every_child_reaches_the_procedure_it_belongs_to() throws Exception {
    for (String table : CHILD_TABLES) {
      assertThat(schema.foreignKeyTargetsOf(table)).contains("licitacion");
    }
  }

  // The natural key and the foreign keys the key does not already lead with, and nothing else:
  // licitacion_id is leftmost in every child's unique constraint, so it needs no index of its own.
  @Test
  void the_award_is_indexed_on_its_key_its_lote_and_its_operador_and_nothing_else()
      throws Exception {
    assertThat(schema.indexNamesOf("licitacion_award"))
        .containsExactlyInAnyOrder(
            "licitacion_award_pkey",
            "licitacion_award_key",
            "licitacion_award_lote_id_idx",
            "licitacion_award_operador_economico_id_idx");
  }

  @Test
  void the_lote_is_indexed_on_its_key_alone() throws Exception {
    assertThat(schema.indexNamesOf("licitacion_lote"))
        .containsExactlyInAnyOrder("licitacion_lote_pkey", "licitacion_lote_key");
  }

  // Every child but the lote orders on the procedure and the lote; the lote has no lote_id of its
  // own, so it orders on the form everything else matches it by.
  private Table table(String name) {
    return "licitacion_lote".equals(name)
        ? Tables.orderedBy(dataSource, name, "lote_key")
        : Tables.orderedBy(dataSource, name, "licitacion_id", "lote_id");
  }
}
