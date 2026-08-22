package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.operador.OperadorId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UteMembershipTest {

  private static final ParticipationId PARTICIPATION_ID =
      new ParticipationId(UUID.fromString("0198c0de-0000-7000-8000-0000000000c1"));
  private static final OperadorId PRACE =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b1"));
  private static final OperadorId TABOADA =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b2"));

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(UteMembership.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("participationId", "operadorId", "withdrawn");
  }

  @Test
  void references_the_participation_rather_than_operador_to_operador_pair() {
    // A membership keyed on two operadores could not express the 33-of-35 case at all: the
    // consortium half of that pair is a party the catalogue does not hold.
    assertThat(new UteMembership(PARTICIPATION_ID, PRACE).participationId())
        .isEqualTo(PARTICIPATION_ID);
  }

  @Test
  void stores_against_the_bid_of_the_consortium_the_source_did_not_identify() {
    // The participation this hangs off carries no operador; the membership is unaffected, which
    // is what makes one shape serve both branches.
    Participation unidentifiedUte =
        new Participation(
            PARTICIPATION_ID,
            new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1")),
            null,
            null,
            true,
            true,
            "UTE PRACE-TABOADA RAMOS",
            false);

    UteMembership membership = new UteMembership(unidentifiedUte.id(), PRACE);

    assertThat(unidentifiedUte.operadorEconomicoId()).isNull();
    assertThat(membership.participationId()).isEqualTo(PARTICIPATION_ID);
    assertThat(membership.operadorId()).isEqualTo(PRACE);
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(new UteMembership(PARTICIPATION_ID, PRACE).withdrawn())
        .isFalse();
  }

  @Test
  void carries_its_own_withdrawal_marker_so_it_can_follow_its_participation() {
    assertThat(new UteMembership(PARTICIPATION_ID, PRACE, true).withdrawn())
        .isTrue();
  }

  @Test
  void requires_the_participation_it_hangs_off() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UteMembership(null, PRACE));
  }

  @Test
  void requires_the_member_firm() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UteMembership(PARTICIPATION_ID, null));
  }

  @Test
  void holds_one_member_once_however_many_times_the_source_lists_it() {
    // The pair is the identity, so it is a value filed under its participation rather than an
    // entity with an identity of its own.
    assertThat(
            new HashSet<>(
                List.of(
                    new UteMembership(PARTICIPATION_ID, PRACE),
                    new UteMembership(PARTICIPATION_ID, PRACE))))
        .hasSize(1);
  }

  @Test
  void keeps_two_members_of_one_consortium_apart() {
    assertThat(
            new HashSet<>(
                List.of(
                    new UteMembership(PARTICIPATION_ID, PRACE),
                    new UteMembership(PARTICIPATION_ID, TABOADA))))
        .hasSize(2);
  }

  @Test
  void keeps_one_firms_memberships_of_two_consortia_apart() {
    ParticipationId otherBid =
        new ParticipationId(UUID.fromString("0198c0de-0000-7000-8000-0000000000c2"));

    assertThat(new UteMembership(PARTICIPATION_ID, PRACE))
        .isNotEqualTo(new UteMembership(otherBid, PRACE));
  }
}
