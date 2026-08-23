package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteRepository;
import gal.conxugal.domain.licitacion.Participation;
import gal.conxugal.domain.licitacion.ParticipationRepository;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * {@link JdbcParticipationRepository} against a real PostgreSQL. A bid now names its party by
 * reference and says nothing else about it, so there are <strong>two shapes and not four</strong>:
 * one that resolved to an operador — a single firm, a member firm or a consortium alike — and one
 * that resolved to nobody because its published identifier was unusable.
 *
 * <p>The consortium is what makes that worth asserting rather than assuming: it used to be the
 * shape needing two extra columns and a {@code CHECK}, and it is now the ordinary one.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcParticipationRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  ParticipationRepository participationRepository;

  @Inject
  LoteRepository loteRepository;

  @Inject
  DataSource dataSource;

  private SchemaFixture catalogue;
  private LicitacionId licitacionId;

  @BeforeEach
  void setUp() throws Exception {
    catalogue = SchemaFixture.joiningTheTestTransaction(dataSource);
    licitacionId = catalogue.licitacion("822054");
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // 578 of 613 measured bidder rows, and under amendment 1 every consortium too: the bid names the
  // party by reference and says nothing else about it.
  @Test
  void bid_stores_under_the_operador_it_resolved_to() throws Exception {
    OperadorId operadorId = catalogue.operador("A41111220", "EQUINSE, S.A.");

    Participation stored =
        participationRepository.upsert(new Participation(licitacionId, null, operadorId, true));

    assertThat(stored.id()).isNotNull();
    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations())
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorId.value())
            .value("won").isTrue();
  }

  // The party whose published identifier was unusable: recorded as neither participant nor awardee
  // of any catalogue entry rather than dropped. The only reason this reference is ever null.
  @Test
  void bid_stores_with_no_operador_where_the_identifier_was_unusable() {
    participationRepository.upsert(new Participation(licitacionId, null, null, false));

    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations()).row(0).value("operador_economico_id").isNull();
  }

  // A consortium is an operador, so its bid is the ordinary shape and not a fourth one. This is
  // the case the earlier model needed two extra columns and a CHECK to express.
  @Test
  void bid_by_consortium_the_source_did_not_identify_stores_like_any_other() throws Exception {
    OperadorId uteId = catalogue.unidentifiedUte("UTE Ponte do Porto");

    participationRepository.upsert(new Participation(licitacionId, null, uteId, true));

    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations())
        .row(0)
            .value("operador_economico_id").isEqualTo(uteId.value())
            .value("won").isTrue();
  }

  // The lotless procedure is the ordinary one -- 85 of 100 measured -- so its bids key on a null
  // lote, which is where NULLS NOT DISTINCT earns its place for this table.
  @Test
  void storing_the_bids_of_lotless_procedure_twice_leaves_one_row_per_bidder() throws Exception {
    OperadorId firmId = catalogue.operador("A41111220", "EQUINSE, S.A.");
    OperadorId otherId = catalogue.operador("B15112222", "AQUAGEST, S.A.");
    OperadorId uteId = catalogue.unidentifiedUte("UTE Ponte do Porto");

    for (int run = 0; run < 2; run++) {
      participationRepository.upsert(new Participation(licitacionId, null, firmId, true));
      participationRepository.upsert(new Participation(licitacionId, null, otherId, false));
      participationRepository.upsert(new Participation(licitacionId, null, uteId, false));
    }

    assertThat(participations()).hasNumberOfRows(3);
    assertThat(lotlessBids()).isEqualTo(3);
  }

  // One operador bidding for two lotes of one procedure is two bids, not a duplicate: the lote is
  // part of the key, which is what lets it win one and lose the other.
  @Test
  void one_operador_bidding_for_two_lotes_holds_one_row_in_each() throws Exception {
    OperadorId firmId = catalogue.operador("A41111220", "EQUINSE, S.A.");
    Lote first = loteRepository.upsert(new Lote(licitacionId, "1", null, null));
    Lote second = loteRepository.upsert(new Lote(licitacionId, "2", null, null));

    for (int run = 0; run < 2; run++) {
      participationRepository.upsert(new Participation(licitacionId, first.id(), firmId, true));
      participationRepository.upsert(new Participation(licitacionId, second.id(), firmId, false));
    }

    assertThat(byOutcome()).hasNumberOfRows(2);
    assertThat(byOutcome())
        .row(0)
            .value("lote_id").isEqualTo(identityOf(second))
            .value("won").isFalse();
    assertThat(byOutcome())
        .row(1)
            .value("lote_id").isEqualTo(identityOf(first))
            .value("won").isTrue();
  }

  // A restatement that moves the award between two bidders of one lote has to be recorded on both
  // rows: won is outside the key, so the loser stops claiming the award rather than keeping it.
  @Test
  void re_storing_one_bid_refreshes_which_of_them_won_in_place() throws Exception {
    OperadorId firmId = catalogue.operador("A41111220", "EQUINSE, S.A.");
    Participation first =
        participationRepository.upsert(new Participation(licitacionId, null, firmId, true));

    Participation corrected =
        participationRepository.upsert(new Participation(licitacionId, null, firmId, false));

    assertThat(corrected.id()).isEqualTo(first.id());
    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations()).row(0).value("won").isFalse();
  }

  // The reconciliation that withdraws a bidder writes through this statement and has no other
  // path, so a withdrawn marker that stopped being bound -- or stopped being refreshed by the
  // conflict clause -- would leave it no way to withdraw anything, silently.
  @Test
  void withdrawing_one_bid_and_publishing_it_again_flips_the_marker_both_ways() throws Exception {
    OperadorId firmId = catalogue.operador("A41111220", "EQUINSE, S.A.");

    participationRepository.upsert(
        new Participation(null, licitacionId, null, firmId, false, true));

    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations()).row(0).value("withdrawn").isTrue();

    participationRepository.upsert(new Participation(licitacionId, null, firmId, false));

    assertThat(participations()).hasNumberOfRows(1);
    assertThat(participations()).row(0).value("withdrawn").isFalse();
  }

  private static UUID identityOf(Lote lote) {
    return Objects.requireNonNull(lote.id(), "the upsert answers the stored lote with its identity")
        .value();
  }

  /**
   * Asked in SQL rather than through a port, because no port offers it: the claim is that all three
   * bids of a lotless procedure key on a <em>null</em> lote, which a row count alone cannot see.
   */
  private long lotlessBids() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT count(*) FROM licitacion_participation"
                    + " WHERE licitacion_id = ? AND lote_id IS NULL")) {
      statement.setObject(1, licitacionId.value());
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getLong(1);
      }
    }
  }

  /**
   * Ordered on the outcome the test set, for the one assertion that has to tell two bids of one
   * operador apart. Ordering them on their lotes would order them on two generated identifiers,
   * whose relative order is the database's business rather than anything the test controls.
   */
  private Table byOutcome() {
    return Tables.orderedBy(dataSource, "licitacion_participation", "won");
  }

  // Ordered on the whole of the key, so row(n) is stable rather than leaning on uuidv7 order.
  private Table participations() {
    return Tables.orderedBy(
        dataSource, "licitacion_participation", "licitacion_id", "lote_id",
        "operador_economico_id");
  }
}
