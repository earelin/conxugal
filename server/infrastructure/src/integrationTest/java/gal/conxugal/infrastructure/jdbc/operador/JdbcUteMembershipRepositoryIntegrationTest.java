package gal.conxugal.infrastructure.jdbc.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

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
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
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
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcUteMembershipRepositoryIntegrationTest implements TestPropertyProvider {

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 822054L);

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

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // The case a membership keyed on a bid could express only from the member's end: the consortium
  // now has a catalogue entry, so the relation reads both ways.
  @Test
  void member_stores_against_consortium_that_holds_no_fiscal_identifier() {
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");

    membershipRepository.upsert(new UteMembership(uteId, memberId));

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships())
        .row(0)
            .value("ute_id").isEqualTo(uteId.value())
            .value("operador_economico_id").isEqualTo(memberId.value())
            .value("withdrawn").isFalse();
  }

  @Test
  void two_members_of_one_consortium_store_two_rows() {
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId firstId = firm("A41111220", "EQUINSE, S.A.");
    OperadorId secondId = firm("B15112222", "AQUAGEST, S.A.");

    membershipRepository.upsert(new UteMembership(uteId, firstId));
    membershipRepository.upsert(new UteMembership(uteId, secondId));

    assertThat(memberships()).hasNumberOfRows(2);
    // Asserted through the port rather than by row index: both members carry generated ids, whose
    // relative order is the database's business rather than anything this test set.
    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(uteId))
        .containsExactlyInAnyOrder(
            new UteMembership(uteId, firstId), new UteMembership(uteId, secondId));
  }

  // Absorbed by the primary key rather than raising, which is what the ON CONFLICT is for: the
  // pair is the identity, so a member the source lists twice is one member.
  @Test
  void upserting_one_member_twice_leaves_one_row() {
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");

    membershipRepository.upsert(new UteMembership(uteId, memberId));
    membershipRepository.upsert(new UteMembership(uteId, memberId));

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
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");

    membershipRepository.upsert(new UteMembership(uteId, memberId));

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
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId firstId = firm("A41111220", "EQUINSE, S.A.");
    OperadorId secondId = firm("B15112222", "AQUAGEST, S.A.");
    membershipRepository.upsert(new UteMembership(uteId, firstId));
    membershipRepository.upsert(new UteMembership(uteId, secondId));

    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(uteId))
        .containsExactlyInAnyOrder(
            new UteMembership(uteId, firstId), new UteMembership(uteId, secondId));
    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(firstId))
        .containsExactly(new UteMembership(uteId, firstId));
  }

  // The storage half of the reachability predicate: a member firm whose only tie is a membership
  // nothing still publishes must not stay reachable through it.
  @Test
  void withdrawn_membership_is_left_out_of_both_visible_reads() {
    OperadorId withdrawnUte = unidentifiedUte("UTE Ponte do Porto");
    OperadorId visibleUte = unidentifiedUte("UTE Ría de Arousa");
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");
    membershipRepository.upsert(new UteMembership(withdrawnUte, memberId, true));
    membershipRepository.upsert(new UteMembership(visibleUte, memberId));

    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(memberId))
        .containsExactly(new UteMembership(visibleUte, memberId));
    assertThat(membershipRepository.findByUteIdAndWithdrawnFalse(withdrawnUte)).isEmpty();
    assertThat(memberships()).hasNumberOfRows(2);
  }

  @Test
  void operador_that_belongs_to_nothing_has_no_visible_memberships() {
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");
    OperadorId strangerId = firm("B15112222", "AQUAGEST, S.A.");
    membershipRepository.upsert(new UteMembership(uteId, memberId));

    assertThat(membershipRepository.findByOperadorIdAndWithdrawnFalse(strangerId)).isEmpty();
  }

  // Re-storing a withdrawn membership as published again un-withdraws it, which is what publishing
  // it again means -- and withdrawn is the only column outside the key for the conflict to refresh.
  @Test
  void re_storing_withdrawn_membership_as_published_makes_it_visible_again() {
    OperadorId uteId = unidentifiedUte("UTE Ponte do Porto");
    OperadorId memberId = firm("A41111220", "EQUINSE, S.A.");
    membershipRepository.upsert(new UteMembership(uteId, memberId, true));

    membershipRepository.upsert(new UteMembership(uteId, memberId));

    assertThat(memberships()).hasNumberOfRows(1);
    assertThat(memberships()).row(0).value("withdrawn").isFalse();
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
