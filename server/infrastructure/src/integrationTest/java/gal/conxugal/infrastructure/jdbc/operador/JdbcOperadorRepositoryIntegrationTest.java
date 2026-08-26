package gal.conxugal.infrastructure.jdbc.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.NomeAlternativo;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
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
    assertThat(found.ute()).isFalse();
    assertThat(found.nameRank()).isEqualTo(new NomeRank(PUBLISHED_ON, 4711L));
  }

  // The marker has to survive the round trip, and nothing else asserts that it does: the record
  // carries a constructor of the arity the aggregate had before it gained the marker, so a mapping
  // that bound the wrong one would answer false for every operador and stay invisible.
  @Test
  void consortium_the_source_identifies_reads_back_carrying_its_marker() {
    OperadorEconomico inserted =
        operadorRepository.insert(
            OperadorEconomico.identifiedUte(
                new FiscalIdentifier("U88779475"), "UTE Ponte", new NomeRank(PUBLISHED_ON, 4711L)));

    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("U88779475")).orElseThrow();

    assertThat(found.id()).isEqualTo(inserted.id());
    assertThat(found.ute()).isTrue();
    assertThat(found.fiscalId()).isEqualTo(new FiscalIdentifier("U88779475"));
  }

  // The marker is set on a row already catalogued, which is the only way a UTE a contrato menor
  // named first ever stops being an ordinary firm — that family imports ahead of licitacións and
  // knows nothing of consortia, so insert is not the path that can reach it.
  @Test
  void operador_another_family_catalogued_unmarked_is_marked_keeping_its_name_and_rank() {
    OperadorEconomico inserted =
        operadorRepository.insert(
            new OperadorEconomico(
                new FiscalIdentifier("U88779475"), "UTE Ponte", new NomeRank(PUBLISHED_ON, 4711L)));

    operadorRepository.markAsUte(inserted.id());

    assertThat(operadorTable())
        .row(0)
            .value("ute").isTrue()
            .value("name").isEqualTo("UTE Ponte")
            .value("name_rank_source_id").isEqualTo(4711L);
  }

  // Idempotent, which is what lets the caller ask without reading the row first.
  @Test
  void marking_an_operador_already_marked_leaves_it_marked() {
    OperadorEconomico inserted =
        operadorRepository.insert(
            OperadorEconomico.unidentifiedUte(
                "UTE PRACE-TABOADA RAMOS", new NomeRank(PUBLISHED_ON, 4711L)));

    operadorRepository.markAsUte(inserted.id());

    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(operadorTable()).row(0).value("ute").isTrue();
  }

  // The 33-of-35 case, stored through the adapter rather than by raw SQL: the row lands with the
  // marker set and the identifier absent, and no lookup can ever reach it again.
  @Test
  void consortium_the_source_declines_to_identify_stores_with_no_identifier() {
    operadorRepository.insert(
        OperadorEconomico.unidentifiedUte(
            "UTE PRACE-TABOADA RAMOS", new NomeRank(PUBLISHED_ON, 4711L)));

    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(operadorTable())
        .row(0)
            .value("fiscal_id").isNull()
            .value("ute").isTrue()
            .value("name").isEqualTo("UTE PRACE-TABOADA RAMOS");
  }

  // The two coexist: an identifier-less consortium collides with neither the other of its kind nor
  // any catalogued firm, which is what leaving the UNIQUE nulls-distinct is for.
  @Test
  void identifier_less_consortium_sits_beside_catalogued_firm_without_colliding() {
    insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON);
    operadorRepository.insert(
        OperadorEconomico.unidentifiedUte("UTE PRACE", new NomeRank(PUBLISHED_ON, 4712L)));
    operadorRepository.insert(
        OperadorEconomico.unidentifiedUte("UTE Ría", new NomeRank(PUBLISHED_ON, 4713L)));

    assertThat(operadorTable()).hasNumberOfRows(3);
    assertThat(operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678"))).isPresent();
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

  // The fold this query applies has to answer what MatchableName answers, since the key it is
  // compared against was computed there. Case, accents and punctuation all go, and this is the one
  // place the two definitions meet.
  @Test
  void find_all_matching_name_folds_case_accents_and_punctuation_of_the_stored_name() {
    OperadorId operadorId =
        idOf(insertOperador("B12345678", "Xestión Ambiental de Contratas, S.L.", PUBLISHED_ON));

    assertThat(matching("XESTION AMBIENTAL DE CONTRATAS SL"))
        .containsExactly(operadorId);
  }

  // R15's retained set is what makes a firm findable under a name it no longer displays, which is
  // the whole reason the match reads both tables rather than the principal name alone.
  @Test
  void find_all_matching_name_finds_the_operador_by_name_it_has_only_retained() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(operadorId, "Galegos, S.L.", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    assertThat(matching("GALEGOS SL"))
        .containsExactly(operadorId);
  }

  // The uniqueness an awardee resolution tests is over operadores, not over names: two spellings
  // of one party are one answer, and a query counting rows would call this an ambiguity.
  @Test
  void find_all_matching_name_answers_once_for_an_operador_holding_the_name_twice_over() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "SERVIZOS GALEGOS, S.L.", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    assertThat(matching("servizos galegos sl"))
        .containsExactly(operadorId);
  }

  @Test
  void find_all_matching_name_answers_both_operadores_that_bear_one_name() {
    OperadorId first = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    OperadorId second = idOf(insertOperador("B87654321", "SERVIZOS GALEGOS, S.L.", PUBLISHED_ON));

    assertThat(matching("Servizos Galegos SL"))
        .containsExactlyInAnyOrder(first, second);
  }

  // SPEC-0006 R3 states it as an absolute — "an entry is never found by anything but a fiscal
  // identifier, so no identifier-less entry can absorb a contract belonging to another party" —
  // and ADR-0023 rests on the same sentence. This query is the one that could break it: a second
  // procedure's award naming a consortium by text would otherwise resolve to the first
  // procedure's, silently and plausibly, and be recorded as though the catalogue had answered.
  @Test
  void find_all_matching_name_never_answers_an_operador_holding_no_fiscal_identifier() {
    operadorRepository.insert(
        OperadorEconomico.unidentifiedUte(
            "UTE PRACE-TABOADA RAMOS", new NomeRank(PUBLISHED_ON, 4711L)));

    assertThat(matching("UTE Prace-Taboada Ramos"))
        .isEmpty();
  }

  // The exclusion has to hold on the retained arm too, and it is not enough to argue that nothing
  // files a name beside such an entry today: this guarantee is too load-bearing to rest on another
  // class's control flow, so the query says it rather than assuming it.
  @Test
  void find_all_matching_name_never_answers_one_by_the_name_it_has_only_retained() {
    OperadorEconomico ute =
        operadorRepository.insert(
            OperadorEconomico.unidentifiedUte(
                "UTE PRACE-TABOADA RAMOS", new NomeRank(PUBLISHED_ON, 4711L)));
    operadorRepository.retainName(
        new NomeAlternativo(ute.id(), "UTE Prace Taboada", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    assertThat(matching("UTE PRACE TABOADA"))
        .isEmpty();
  }

  // And the identified consortium is untouched by the exclusion: it holds an identifier like any
  // other operador, so the derivation reaches it exactly as it reaches a single firm.
  @Test
  void find_all_matching_name_still_answers_the_consortium_the_source_identified() {
    OperadorEconomico ute =
        operadorRepository.insert(
            OperadorEconomico.identifiedUte(
                new FiscalIdentifier("U88779475"),
                "UTE INSIDE OVIGA RIBADEO",
                new NomeRank(PUBLISHED_ON, 4711L)));

    assertThat(matching("ute inside oviga ribadeo"))
        .containsExactly(ute.id());
  }

  @Test
  void find_all_matching_name_answers_nobody_for_name_the_catalogue_does_not_hold() {
    insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON);

    assertThat(matching("EQUINSE, S.A."))
        .isEmpty();
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

  // The other half of the same statement: dropping the operador column from its WHERE would take
  // every operador's copy of the promoted name, and no test asserting one operador's rows could
  // see it. Two of them hold the name, one promotes it, and the other's row must survive.
  @Test
  void promote_name_stops_retaining_the_name_for_its_own_operador_only() {
    OperadorId promoting = idOf(insertOperador("B12345678", "Servizos SL", PUBLISHED_ON));
    OperadorId untouched = idOf(insertOperador("B87654321", "Obras SL", PUBLISHED_ON));
    operadorRepository.retainName(
        new NomeAlternativo(promoting, "Grupo Galego", new NomeRank(PUBLISHED_EARLIER, 4700L)));
    operadorRepository.retainName(
        new NomeAlternativo(untouched, "Grupo Galego", new NomeRank(PUBLISHED_EARLIER, 4600L)));

    operadorRepository.promoteName(
        promoting, "Grupo Galego", new NomeRank(PUBLISHED_LATER, 4800L));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("operador_economico_id").isEqualTo(untouched.value())
            .value("name").isEqualTo("Grupo Galego")
            .value("last_published_source_id").isEqualTo(4600L);
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
  // and it must cost nothing here. Asserted on xmin — the transaction that last wrote the row —
  // because the values alone would hold just as well if the upsert had fired and rewritten them,
  // which is not what "leaves the retention unchanged" claims.
  @Test
  void retaining_the_same_name_with_the_same_contract_leaves_the_row_untouched() throws Exception {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    NomeAlternativo retained =
        new NomeAlternativo(
            operadorId, "Servizos Galegos", new NomeRank(PUBLISHED_EARLIER, 4700L));
    operadorRepository.retainName(retained);
    String versionBefore = tupleVersionOf(operadorId, "Servizos Galegos");

    operadorRepository.retainName(retained);

    assertThat(tupleVersionOf(operadorId, "Servizos Galegos")).isEqualTo(versionBefore);
    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("last_published_date").isEqualTo(PUBLISHED_EARLIER)
            .value("last_published_source_id").isEqualTo(4700L);
  }

  // The state the aggregate throws on being built in, and one nothing could recover from: the
  // port offers no delete, so a single such row would make this operador unreadable for good.
  @Test
  void retain_name_refuses_the_name_the_operador_is_displayed_under() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos SL", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    assertThat(nomeAlternativoTable()).hasNumberOfRows(0);
    assertThat(operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")))
        .isPresent();
  }

  // The guard is on the displayed name, not on the operador: the same spelling is an ordinary
  // alternative for anyone not displaying it.
  @Test
  void retain_name_accepts_the_name_another_operador_is_displayed_under() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));
    insertOperador("B87654321", "Obras do Miño SL", PUBLISHED_ON);

    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Obras do Miño SL", new NomeRank(PUBLISHED_EARLIER, 4700L)));

    Table alternativos = nomeAlternativoTable();
    assertThat(alternativos).hasNumberOfRows(1);
    assertThat(alternativos)
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorId.value())
            .value("name").isEqualTo("Obras do Miño SL");
  }

  // Promote first, then retain the displaced name: the order the guard above imposes, walked end
  // to end so that the sequence the derivation performs is proved rather than assumed. Neither
  // name ends up in both places, which is the invariant a later read is built on.
  @Test
  void promoting_then_retaining_the_displaced_name_leaves_neither_name_in_both_places() {
    OperadorId operadorId = idOf(insertOperador("B12345678", "Servizos Galegos SL", PUBLISHED_ON));

    operadorRepository.promoteName(
        operadorId, "Servizos Galegos SLU", new NomeRank(PUBLISHED_LATER, 4800L));
    operadorRepository.retainName(
        new NomeAlternativo(
            operadorId, "Servizos Galegos SL", new NomeRank(PUBLISHED_ON, 4711L)));

    OperadorEconomico found =
        operadorRepository.findByFiscalId(new FiscalIdentifier("B12345678")).orElseThrow();
    assertThat(found.name()).isEqualTo("Servizos Galegos SLU");
    assertThat(found.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactly("Servizos Galegos SL");
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

  private Set<OperadorId> matching(String publishedName) {
    return operadorRepository.findAllMatchingName(MatchableName.of(publishedName).orElseThrow());
  }

  private OperadorEconomico insertOperador(
      String fiscalId, String name, @Nullable LocalDate rankDate) {
    return operadorRepository.insert(
        new OperadorEconomico(
            new FiscalIdentifier(fiscalId), name, new NomeRank(rankDate, 4711L)));
  }

  // Where the row physically sits, which is what tells an upsert that did nothing from one that
  // rewrote the same values: PostgreSQL never updates a tuple in place, so a DO UPDATE that fired
  // leaves a new version at a new ctid. Not xmin — every write of one test shares a transaction,
  // so that would read as unchanged whether the statement fired or not. Read as text because
  // neither type has a JDBC mapping of its own.
  private String tupleVersionOf(OperadorId operadorId, String name) throws Exception {
    String sql =
        "SELECT ctid::text AS version FROM operador_economico_nome_alternativo"
            + " WHERE operador_economico_id = ? AND name = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, operadorId.value());
      statement.setString(2, name);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No name %s retained beside %s".formatted(
              name, operadorId));
        }
        return resultSet.getString("version");
      }
    }
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
