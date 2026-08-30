package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.licitacion.LicitacionId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UteMembershipTest {

  private static final OperadorId UTE_ID =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000c1"));
  private static final OperadorId MEMBER_ID =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b1"));
  private static final OperadorId OTHER_MEMBER_ID =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b2"));
  private static final LicitacionId STATED_BY =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final LicitacionId STATED_BY_ANOTHER =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a2"));

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(UteMembership.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("uteId", "operadorId", "licitacionId", "withdrawn");
  }

  // Both ends are catalogue entries, which is what lets the relation be read from either side.
  // Keyed on a bid instead, the consortium's own end would have nothing to open.
  @Test
  void relates_one_operador_to_another() {
    UteMembership membership = new UteMembership(UTE_ID, MEMBER_ID, STATED_BY);

    assertThat(membership.uteId()).isEqualTo(UTE_ID);
    assertThat(membership.operadorId()).isEqualTo(MEMBER_ID);
  }

  // What makes an identified UTE reconcilable: the same pair stated by two procedures is two
  // rows, so one procedure withdrawing its statement cannot hide what the other still publishes.
  @Test
  void is_stated_by_one_procedure_so_two_procedures_state_it_separately() {
    UteMembership membership = new UteMembership(UTE_ID, MEMBER_ID, STATED_BY);

    assertThat(membership.licitacionId()).isEqualTo(STATED_BY);
    assertThat(membership).isNotEqualTo(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY_ANOTHER));
  }

  @Test
  void requires_the_procedure_that_states_it() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UteMembership(UTE_ID, MEMBER_ID, null));
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY).withdrawn()).isFalse();
  }

  @Test
  void requires_the_consortium_it_relates() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UteMembership(null, MEMBER_ID, STATED_BY));
  }

  @Test
  void requires_the_member_firm() {
    assertThatNullPointerException().isThrownBy(() -> new UteMembership(UTE_ID, null, STATED_BY));
  }

  // The pair being the key, a self-membership would be a row saying nothing, and the reachability
  // predicate would then count a consortium as keeping itself reachable.
  @Test
  void refuses_consortium_that_is_its_own_member() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UteMembership(UTE_ID, UTE_ID, STATED_BY));
  }

  // It holds no identity of its own, so the record's own equality is the correct one — and the
  // architecture rule forbids overriding it, every component being another aggregate's id.
  @Test
  void compares_by_its_components_because_it_holds_no_identity_of_its_own() {
    assertThat(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY))
        .isEqualTo(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY))
        .hasSameHashCodeAs(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY));
  }

  @Test
  void is_different_membership_for_different_member() {
    assertThat(new UteMembership(UTE_ID, MEMBER_ID, STATED_BY))
        .isNotEqualTo(new UteMembership(UTE_ID, OTHER_MEMBER_ID, STATED_BY));
  }

  // Named rather than discovered: the equality is the quadruple, so one stored row read either
  // side of a withdrawal is two values to a set, which is a trap for a caller that collects them.
  @Test
  void treats_two_readings_either_side_of_withdrawal_as_two_values() {
    assertThat(
            new HashSet<>(
                List.of(
                    new UteMembership(UTE_ID, MEMBER_ID, STATED_BY, false),
                    new UteMembership(UTE_ID, MEMBER_ID, STATED_BY, true))))
        .hasSize(2);
  }
}
