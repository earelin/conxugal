package gal.conxugal.infrastructure.jdbc.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
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
 * {@link JdbcUteMembershipRepository} against a real PostgreSQL. Three claims carry this class:
 * that a membership stores against a consortium holding <strong>no fiscal identifier</strong> —
 * the 33-of-35 case, and the whole reason the catalogue admits one; that it reads from
 * <strong>both ends</strong>, which is what moving it off the bid bought; and that a
 * <strong>withdrawn</strong> membership leaves an operador's visible set, which is the storage
 * half of the reachability predicate.
 *
 * <p>A fourth carries the reconciliation: a membership is a statement by <strong>one
 * procedure</strong>, so withdrawing what this procedure no longer states leaves what another
 * still does — which is the only thing that keeps an identified UTE, one operador across every
 * procedure naming it, reconcilable at all.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcUteMembershipRepositoryIntegrationTest implements TestPropertyProvider {

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 822054L);

  private static final String MEMBER_FISCAL_ID = "A41111220";
  private static final String MEMBER_NAME = "EQUINSE, S.A.";
  private static final String OTHER_MEMBER_FISCAL_ID = "B15112222";
  private static final String OTHER_MEMBER_NAME = "AQUAGEST, S.A.";
  private static final String CONSORTIUM_NAME = "UTE Ponte do Porto";

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  OperadorRepository operadorRepository;

  @Inject
  UteMembershipRepository membershipRepository;

  @Inject
  DataSource dataSource;

  private LicitacionId statedBy;
  private @Nullable UUID stateId;

  @BeforeEach
  void oneProcedureStatesTheMembership() throws Exception {
    stateId = null;
    statedBy = licitacion("822054");
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // The case a membership keyed on a bid could express only from the member's end: the consortium
  // now has a catalogue entry, so the relation reads both ways.
  @Test
  void member_stores_against_consortium_that_holds_no_fiscal_identifier() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);

    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships())
        .row(0)
            .value("ute_id").isEqualTo(uteId.value())
            .value("operador_economico_id").isEqualTo(memberId.value())
            .value("withdrawn").isFalse();
  }

  @Test
  void two_members_of_one_consortium_store_two_rows() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId firstId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    OperadorId secondId = firm(OTHER_MEMBER_FISCAL_ID, OTHER_MEMBER_NAME);

    membershipRepository.upsert(new UteMembership(uteId, firstId, statedBy));
    membershipRepository.upsert(new UteMembership(uteId, secondId, statedBy));

    assertThat(memberships()).hasNumberOfRows(2);
    // Asserted through the port rather than by row index: both members carry generated ids, whose
    // relative order is the database's business rather than anything this test set.
    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(uteId))
        .containsExactlyInAnyOrder(
            new UteMembership(uteId, firstId, statedBy),
            new UteMembership(uteId, secondId, statedBy));
  }

  // Absorbed by the primary key rather than raising, which is what the ON CONFLICT is for: the
  // pair is the identity, so a member the source lists twice is one member.
  @Test
  void upserting_one_member_twice_leaves_one_row() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);

    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));
    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    assertThat(memberships()).hasNumberOfRows(1);
  }

  // The identified consortium -- 2 of 35 -- stores the same membership rows, and only whether its
  // operador holds an identifier differs. That is what makes one shape serve both branches.
  @Test
  void member_of_identified_consortium_stores_the_same_row() {
    OperadorId uteId =
        identityOf(
            operadorRepository.insert(
                OperadorEconomico.identifiedUte(
                    new FiscalIdentifier("U88779475"), "UTE Ponte", RANK)));
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);

    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships())
        .row(0)
            .value("ute_id").isEqualTo(uteId.value())
            .value("operador_economico_id").isEqualTo(memberId.value());
  }

  // The direction the earlier model could not answer for an unidentified consortium: it had no
  // catalogue entry to open, so *who was this made of* lived only on the licitación's page.
  @Test
  void the_relation_reads_from_both_ends() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId firstId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    OperadorId secondId = firm(OTHER_MEMBER_FISCAL_ID, OTHER_MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, firstId, statedBy));
    membershipRepository.upsert(new UteMembership(uteId, secondId, statedBy));

    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(uteId))
        .containsExactlyInAnyOrder(
            new UteMembership(uteId, firstId, statedBy),
            new UteMembership(uteId, secondId, statedBy));
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(firstId))
        .containsExactly(new UteMembership(uteId, firstId, statedBy));
  }

  // The storage half of the reachability predicate: a member firm whose only tie is a membership
  // nothing still publishes must not stay reachable through it.
  @Test
  void withdrawn_membership_is_left_out_of_both_visible_reads() {
    OperadorId withdrawnUte = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId visibleUte = unidentifiedUte("UTE Ría de Arousa");
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(withdrawnUte, memberId, statedBy, true));
    membershipRepository.upsert(new UteMembership(visibleUte, memberId, statedBy));

    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(memberId))
        .containsExactly(new UteMembership(visibleUte, memberId, statedBy));
    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(withdrawnUte)).isEmpty();
    assertThat(memberships()).hasNumberOfRows(2);
  }

  @Test
  void operador_that_belongs_to_nothing_has_no_visible_memberships() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    OperadorId strangerId = firm(OTHER_MEMBER_FISCAL_ID, OTHER_MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(strangerId)).isEmpty();
  }

  // Re-storing a withdrawn membership as published again un-withdraws it, which is what publishing
  // it again means -- and withdrawn is the only column outside the key for the conflict to refresh.
  @Test
  void re_storing_withdrawn_membership_as_published_makes_it_visible_again() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy, true));

    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships()).row(0).value("withdrawn").isFalse();
  }

  // The reconciliation's ordinary case: the record restates the consortium with one member gone,
  // and the statement this procedure no longer makes stops being shown without being erased.
  @Test
  void withdraws_the_statements_this_procedure_no_longer_makes() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId keptId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    OperadorId droppedId = firm(OTHER_MEMBER_FISCAL_ID, OTHER_MEMBER_NAME);
    UteMembership kept = new UteMembership(uteId, keptId, statedBy);
    membershipRepository.upsert(kept);
    membershipRepository.upsert(new UteMembership(uteId, droppedId, statedBy));

    membershipRepository.withdrawAbsent(statedBy, List.of(kept));

    assertThat(memberships()).hasNumberOfRows(2);
    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(uteId)).containsExactly(kept);
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(droppedId)).isEmpty();
  }

  // The consortium the record stopped publishing altogether: nothing is retained, so everything
  // this procedure stated goes -- and the empty set is the ordinary case, not a caller's slip.
  @Test
  void withdraws_every_statement_when_the_record_publishes_no_consortium() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));

    membershipRepository.withdrawAbsent(statedBy, List.of());

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(memberId)).isEmpty();
  }

  // The identified minority, and the reason licitacion_id is in the key: one UTE operador is
  // published by two procedures, so a withdrawal at one must not hide what the other still states.
  @Test
  void withdrawal_at_one_procedure_leaves_what_another_still_states() throws Exception {
    LicitacionId statedByAnother = licitacion("822055");
    OperadorId uteId =
        identityOf(
            operadorRepository.insert(
                OperadorEconomico.identifiedUte(
                    new FiscalIdentifier("U88779475"), "UTE Ponte", RANK)));
    OperadorId sharedId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    OperadorId onlyHereId = firm(OTHER_MEMBER_FISCAL_ID, OTHER_MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, sharedId, statedBy));
    membershipRepository.upsert(new UteMembership(uteId, onlyHereId, statedBy));
    membershipRepository.upsert(new UteMembership(uteId, sharedId, statedByAnother));

    membershipRepository.withdrawAbsent(statedBy, List.of());

    // The shared member stays reachable through the other procedure's statement; the one only this
    // procedure ever made is gone, because now no procedure makes it.
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(sharedId))
        .containsExactly(new UteMembership(uteId, sharedId, statedByAnother));
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(onlyHereId)).isEmpty();
  }

  // Withdrawal is not a rewrite: a re-import that changes nothing must leave rows it marked on an
  // earlier run untouched, which is what makes an unchanged restatement observably free.
  @Test
  void withdrawing_twice_marks_nothing_the_second_time() {
    OperadorId uteId = unidentifiedUte(CONSORTIUM_NAME);
    OperadorId memberId = firm(MEMBER_FISCAL_ID, MEMBER_NAME);
    membershipRepository.upsert(new UteMembership(uteId, memberId, statedBy));
    membershipRepository.withdrawAbsent(statedBy, List.of());

    membershipRepository.withdrawAbsent(statedBy, List.of());

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships()).row(0).value("withdrawn").isTrue();
  }

  /**
   * A procedure for the memberships to be stated by. Raw SQL rather than the licitacións package's
   * own fixture, which is package-private to it — three inserts is less than promoting a fixture
   * across packages for one column's sake.
   */
  private LicitacionId licitacion(String publicationId) throws SQLException {
    UUID organoId =
        insertReturningId(
            "INSERT INTO organo_contratacion (id, source_key, name, active)"
                + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id",
            "consorcio-" + publicationId,
            "Consorcio " + publicationId);
    // One state row for every procedure this class stores: the vocabulary is keyed on the code the
    // source publishes, so a second insert of the same code is a duplicate rather than a fixture.
    if (stateId == null) {
      stateId =
          insertReturningId(
              "INSERT INTO licitacion_state (code, label) VALUES (?, ?) RETURNING id",
              2,
              "Adxudicado");
    }
    return new LicitacionId(
        insertReturningId(
            "INSERT INTO licitacion (publication_id, organo_id, state_id) VALUES (?, ?, ?)"
                + " RETURNING id",
            publicationId,
            organoId,
            stateId));
  }

  private UUID insertReturningId(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return rows.getObject("id", UUID.class);
      }
    }
  }

  private OperadorId unidentifiedUte(String name) {
    return identityOf(operadorRepository.insert(OperadorEconomico.unidentifiedUte(name, RANK)));
  }

  private OperadorId firm(String fiscalId, String name) {
    return identityOf(
        operadorRepository.insert(
            new OperadorEconomico(new FiscalIdentifier(fiscalId), name, RANK)));
  }

  private static OperadorId identityOf(OperadorEconomico operador) {
    return Objects.requireNonNull(
        operador.id(), "the insert answers the stored operador with its identity");
  }

  // Ordered on the whole of the key, so row(n) is stable rather than leaning on insertion order.
  private Table memberships() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("operador_ute_membership")
        .columnsToOrder(
            new Table.Order[] {
              Table.Order.asc("ute_id"), Table.Order.asc("operador_economico_id")
            })
        .build();
  }
}
