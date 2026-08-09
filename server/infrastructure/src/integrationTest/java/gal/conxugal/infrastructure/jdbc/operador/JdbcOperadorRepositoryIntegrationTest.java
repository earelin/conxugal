package gal.conxugal.infrastructure.jdbc.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeAlternativo;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The adapter against a real PostgreSQL. What a write left behind is asserted over the tables
 * rather than read back through the same adapter, so a statement that touched rows it should not
 * have cannot pass; the lookups are the exception, because there the read is what is under test.
 *
 * <p>Every test here expects its writes to succeed. The unique fiscal identifier and the foreign
 * key that keeps a retained name from outliving its operador are asserted in {@link
 * OperadorEconomicoMigrationIntegrationTest}, which commits for exactly that reason.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcOperadorRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);
  private static final LocalDate PUBLISHED_EARLIER = LocalDate.of(2026, 1, 5);
  private static final LocalDate PUBLISHED_LATER = LocalDate.of(2026, 6, 1);

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @Inject
  OperadorRepository operadorRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // Typed aggregate identifiers rest on Micronaut Data populating an id that travels through an
  // AttributeConverter. The id is checked against the table rather than against the record the
  // insert answered with, which would hold by construction.
  @Test
  void insert_assigns_the_generated_identity_and_stores_the_canonical_identifier() {
    OperadorEconomico inserted =
        operadorRepository.insert(
            new OperadorEconomico(
                new FiscalIdentifier("  b12345678  "),
                "Servizos Galegos SL",
                new NomeRank(PUBLISHED_ON, 4711L)));

    OperadorId insertedId = inserted.id();
    assertThat(insertedId).isNotNull();
    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores)
        .row(0)
            .value("id").isEqualTo(insertedId.value())
            .value("fiscal_id").isEqualTo("B12345678")
            .value("name").isEqualTo("Servizos Galegos SL")
            .value("name_rank_date").isEqualTo(PUBLISHED_ON)
            .value("name_rank_source_id").isEqualTo(4711L);
  }

  // The store holds no spelling but the canonical one, so this is what a later read has to
  // return whichever published spelling created the row.
  @Test
  void stored_operador_reads_back_carrying_its_identity_and_upper_cased_identifier() {
    OperadorEconomico inserted = insertOperador("b12345678", "Servizos Galegos SL", PUBLISHED_ON);

    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")).orElseThrow();

    assertThat(found).isEqualTo(inserted);
    assertThat(found.id()).isEqualTo(inserted.id());
    assertThat(found.fiscalId()).isEqualTo(new FiscalIdentifier("B12345678"));
    assertThat(found.name()).isEqualTo("Servizos Galegos SL");
    assertThat(found.nameRank()).isEqualTo(new NomeRank(PUBLISHED_ON, 4711L));
  }

  @Test
  void finds_the_operador_from_padded_and_differently_cased_identifier() {
    insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON);

    Optional<OperadorEconomico> found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("  b12345678  "));

    assertThat(found).isPresent();
  }

  // Over-merging two real suppliers is as damaging as splitting one, so the reduction stops at
  // padding and case: internal spacing, punctuation and a differing character are other
  // operadores.
  @Test
  void finds_nothing_for_an_identifier_differing_beyond_padding_and_case() {
    insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON);

    assertThat(operadorRepository.findByFiscalId(new FiscalIdentifier("B1234 5678"))).isEmpty();
    assertThat(operadorRepository.findByFiscalId(new FiscalIdentifier("B-12345678"))).isEmpty();
    assertThat(operadorRepository.findByFiscalId(new FiscalIdentifier("B12345679"))).isEmpty();
  }

  @Test
  void finds_the_operador_with_every_name_it_has_retained() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "SERVIZOS GALEGOS SL", new NomeRank(null, 4690L)));

    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")).orElseThrow();

    assertThat(found.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactlyInAnyOrder("Servizos Galegos", "SERVIZOS GALEGOS SL");
    assertThat(found.nomesAlternativos())
        .extracting(NomeAlternativo::lastPublished)
        .containsExactlyInAnyOrder(
            new NomeRank(PUBLISHED_EARLIER, 4700L), new NomeRank(null, 4690L));
  }

  @Test
  void finds_an_operador_that_has_borne_one_name_holding_no_alternatives() {
    insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON);

    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")).orElseThrow();

    assertThat(found.nomesAlternativos()).isEmpty();
  }

  // All three or none: a row carrying a name from one contract and a rank from another would
  // make the display rule undecidable on the next comparison.
  @Test
  void promote_name_writes_the_name_and_both_rank_values_together() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));

    operadorRepository.promoteName(
        operadorId, "Servizos Galegos SLU", new NomeRank(PUBLISHED_LATER, 4800L));

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores)
        .row(0)
            .value("fiscal_id").isEqualTo("B12345678")
            .value("name").isEqualTo("Servizos Galegos SLU")
            .value("name_rank_date").isEqualTo(PUBLISHED_LATER)
            .value("name_rank_source_id").isEqualTo(4800L);
  }

  // The aggregate refuses to be built holding its own displayed name as an alternative, so a
  // promotion that left the retained copy behind would write a state no later read could load.
  @Test
  void promote_name_stops_retaining_the_name_it_promoted() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Galegos SL", new NomeRank(PUBLISHED_EARLIER, 4690L)));

    operadorRepository.promoteName(
        operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_LATER, 4800L));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("name").isEqualTo("Galegos SL")
            .value("last_published_source_id").isEqualTo(4690L);
  }

  @Test
  void promote_name_leaves_the_retained_set_alone_when_the_promoted_name_was_never_retained() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    operadorRepository.promoteName(
        operadorId, "Galegos Servizos SL", new NomeRank(PUBLISHED_LATER, 4800L));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("name").isEqualTo("Servizos Galegos")
            .value("last_published_source_id").isEqualTo(4700L);
  }

  @Test
  void promote_name_touches_no_other_operador() {
    OperadorId promoted = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    insertOperador("B87654321", "Obras do Miño SL", PUBLISHED_ON);

    operadorRepository.promoteName(
        promoted, "Servizos Galegos SLU", new NomeRank(PUBLISHED_LATER, 4800L));

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(2);
    assertThat(operadores)
        .row(0)
            .value("fiscal_id").isEqualTo("B12345678")
            .value("name").isEqualTo("Servizos Galegos SLU");
    assertThat(operadores)
        .row(1)
            .value("fiscal_id").isEqualTo("B87654321")
            .value("name").isEqualTo("Obras do Miño SL")
            .value("name_rank_source_id").isEqualTo(4711L);
  }

  // A contract whose publication date could not be interpreted still names an operador, and the
  // null it stores has to survive the write, the read and the next comparison.
  @Test
  void operador_whose_winning_contract_has_no_date_stores_null_rank_date_and_is_still_found() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", null));

    operadorRepository.promoteName(operadorId, "Servizos Galegos SLU", new NomeRank(null, 4800L));

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores)
        .row(0)
            .value("name").isEqualTo("Servizos Galegos SLU")
            .value("name_rank_date").isNull()
            .value("name_rank_source_id").isEqualTo(4800L);
    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")).orElseThrow();
    assertThat(found.nameRank()).isEqualTo(new NomeRank(null, 4800L));
  }

  @Test
  void retain_name_adds_row_for_name_the_operador_does_not_hold() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorId.value())
            .value("name").isEqualTo("Servizos Galegos")
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4700L);
  }

  // One row per distinct name however many contracts publish it: the row count is the assertion,
  // because the failure this guards is a table that grows per award.
  @Test
  void retain_name_advances_name_already_held_in_place_and_adds_no_second_row() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_LATER, 4800L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("name").isEqualTo("Servizos Galegos")
            .value("last_published_date").isEqualTo(PUBLISHED_LATER)
            .value("last_published_source_id").isEqualTo(4800L);
  }

  // Re-reading a contract already held is what a resumption overlap and a whole re-run both do,
  // and it must cost nothing here.
  @Test
  void retaining_the_same_name_with_the_same_contract_leaves_the_row_unchanged() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    NomeAlternativo retained =
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L));
    operadorRepository.retainName(retained);

    operadorRepository.retainName(retained);

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4700L);
  }

  // The retained fact is the most recent contract that published the name, not the last one to
  // arrive — a walk reads newest first, so an older contract arriving later is ordinary.
  @Test
  void retain_name_leaves_the_stored_rank_alone_when_an_older_contract_publishes_it() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_LATER, 4800L)));

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isEqualTo(PUBLISHED_LATER)
            .value("last_published_source_id").isEqualTo(4800L);
  }

  // An undated rank loses to every dated one however high its source identifier, which is what
  // coalescing an absent date to -infinity buys. Comparing the columns directly would answer
  // NULL here, and a NULL condition skips the update silently.
  @Test
  void retain_name_advances_an_undated_row_when_dated_contract_publishes_the_name() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(null, 4900L)));

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4700L);
  }

  @Test
  void retain_name_never_lets_an_undated_contract_displace_dated_one() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(null, 4900L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4700L);
  }

  // Two names seen only on undated contracts still order against one another, by the higher
  // source identifier — the tie-break is what keeps the rule total.
  @Test
  void retain_name_orders_two_undated_contracts_by_the_higher_source_identifier() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(null, 4700L)));

    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(null, 4800L)));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Servizos Galegos", new NomeRank(null, 4600L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isNull()
            .value("last_published_source_id").isEqualTo(4800L);
  }

  // Nothing folds a published name at the store any more than in the domain: normalising one is
  // forbidden, so the key is the name exactly as published.
  @Test
  void two_names_differing_only_in_letter_case_are_retained_as_two_rows() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "SERVIZOS GALEGOS", new NomeRank(PUBLISHED_EARLIER, 4690L)));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(2);
    assertThat(alternativos).row(0).value("name").isEqualTo("SERVIZOS GALEGOS");
    assertThat(alternativos).row(1).value("name").isEqualTo("Servizos Galegos");
  }

  // The name alone is the value's identity, so nothing in the domain stops a name filed under
  // one operador being compared with a name filed under another. The column is what scopes it,
  // and one operador's retention must not reach the other's row.
  @Test
  void retaining_one_operadors_name_leaves_the_same_name_under_another_operador_alone() {
    OperadorId advanced = idOf(insertOperador("B12345678", "Servizos SL", PUBLISHED_ON));
    OperadorId untouched = idOf(insertOperador("B87654321", "Obras SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(advanced, "Grupo Galego", new NomeRank(PUBLISHED_EARLIER, 4700L)));
    operadorRepository.retainName(
        new NomeAlternativo(untouched, "Grupo Galego", new NomeRank(PUBLISHED_EARLIER, 4600L)));

    operadorRepository.retainName(
        new NomeAlternativo(advanced, "Grupo Galego", new NomeRank(PUBLISHED_LATER, 4800L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(2);
    assertThat(alternativos)
        .row(0)
            .value("operador_economico_id").isEqualTo(untouched.value())
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4600L);
    assertThat(alternativos)
        .row(1)
            .value("operador_economico_id").isEqualTo(advanced.value())
            .value("last_published_date").isEqualTo(PUBLISHED_LATER)
            .value("last_published_source_id").isEqualTo(4800L);
  }

  @Test
  void refuses_retaining_name_beside_operador_that_has_not_been_stored() {
    NomeAlternativo unfiled =
        new NomeAlternativo(null, "Servizos Galegos", new NomeRank(PUBLISHED_ON, 4711L));

    assertThatThrownBy(() -> operadorRepository.retainName(unfiled))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Servizos Galegos");
  }

  private OperadorEconomico insertOperador(
      String fiscalId, String name, @Nullable LocalDate rankDate) {
    return operadorRepository.insert(
        new OperadorEconomico(
            new FiscalIdentifier(fiscalId), name, new NomeRank(rankDate, 4711L)));
  }

  private static OperadorId idOf(OperadorEconomico operador) {
    return Objects.requireNonNull(
        operador.id(), "the database assigns an operador its identity on insert");
  }

  // Ordered so row(n) is stable, rather than leaning on uuidv7 insertion order. The alternatives
  // order by the rank's source identifier, which every test below keeps distinct, because the
  // name column carries the Galician collation and the operador column carries a generated id.
  private Table operadorTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("operador_economico")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("fiscal_id")})
        .build();
  }

  private Table nomeAlternativoTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("operador_economico_nome_alternativo")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("last_published_source_id")})
        .build();
  }
}
