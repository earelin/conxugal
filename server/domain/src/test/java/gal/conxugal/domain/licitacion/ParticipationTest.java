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

class ParticipationTest {

  private static final LicitacionId LICITACION_ID =
      new LicitacionId(UUID.fromString("0198c0de-0000-7000-8000-0000000000f1"));
  private static final LoteId LOTE_ID =
      new LoteId(UUID.fromString("0198c0de-0000-7000-8000-0000000000a1"));
  private static final OperadorId OPERADOR_ID =
      new OperadorId(UUID.fromString("0198c0de-0000-7000-8000-0000000000b1"));

  // No consortium marker and no published name: a consortium is an operador like any other party,
  // so this row says who bid by reference and holds no description of them at all.
  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(Participation.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "id", "licitacionId", "loteId", "operadorEconomicoId", "won", "withdrawn");
  }

  @Test
  void constructs_as_bidder_the_catalogue_holds() {
    // 578 of 613 measured bidder rows, every one carrying an ordinary fiscal identifier — and,
    // under amendment 1, every consortium too.
    Participation firm = bid(OPERADOR_ID);

    assertThat(firm.operadorEconomicoId()).isEqualTo(OPERADOR_ID);
  }

  @Test
  void constructs_as_bidder_the_catalogue_could_not_hold() {
    // The row whose published identifier was unusable: recorded as neither participant nor
    // awardee of any catalogue entry, rather than dropped. This is the only reason the reference
    // is null — a consortium is catalogued, so it is never this case.
    Participation unresolved = bid(null);

    assertThat(unresolved.operadorEconomicoId()).isNull();
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_it_has_no_lotes() {
    assertThat(bid(OPERADOR_ID).loteId()).isNull();
  }

  @Test
  void marks_the_bid_that_won_so_one_operador_can_win_lote_and_lose_another() {
    Participation winner = new Participation(LICITACION_ID, LOTE_ID, OPERADOR_ID, true);
    Participation loser = new Participation(LICITACION_ID, null, OPERADOR_ID, false);

    assertThat(winner.won()).isTrue();
    assertThat(loser.won()).isFalse();
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(bid(OPERADOR_ID).id()).isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(bid(OPERADOR_ID).withdrawn()).isFalse();
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Participation(null, null, OPERADOR_ID, false));
  }

  @Test
  void is_the_same_participation_as_itself_whether_stored_or_not() {
    Participation identified = stored(new ParticipationId(UUID.randomUUID()));
    Participation sameIdentified = identified;
    Participation unstored = bid(OPERADOR_ID);
    Participation sameUnstored = unstored;

    assertThat(identified).isEqualTo(sameIdentified);
    assertThat(unstored).isEqualTo(sameUnstored);
  }

  @Test
  void is_not_equal_to_null_or_to_anything_that_is_not_participation() {
    Participation identified = stored(new ParticipationId(UUID.randomUUID()));

    assertThat(identified)
        .isNotEqualTo(null)
        .isNotEqualTo(new LicitacionContractType("Obras"));
  }

  @Test
  void treats_two_readings_of_one_stored_participation_as_the_same_participation() {
    ParticipationId id = new ParticipationId(UUID.randomUUID());

    assertThat(stored(id))
        .isEqualTo(stored(id))
        .hasSameHashCodeAs(stored(id));
  }

  // A restatement that moves the award between two bidders of one lote rewrites this marker, and
  // it is the same bid throughout — which is what equality by identity is for.
  @Test
  void stays_the_same_bid_when_the_award_moves_off_it() {
    ParticipationId id = new ParticipationId(UUID.randomUUID());

    Participation beforeRestating =
        new Participation(id, LICITACION_ID, LOTE_ID, OPERADOR_ID, true, false);
    Participation afterRestating =
        new Participation(id, LICITACION_ID, LOTE_ID, OPERADOR_ID, false, false);

    assertThat(beforeRestating)
        .isEqualTo(afterRestating)
        .hasSameHashCodeAs(afterRestating);
  }

  @Test
  void treats_participations_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(bid(OPERADOR_ID), bid(OPERADOR_ID)))).hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Participation unstored = bid(OPERADOR_ID);
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static Participation bid(OperadorId operadorId) {
    return new Participation(LICITACION_ID, null, operadorId, false);
  }

  private static Participation stored(ParticipationId id) {
    return new Participation(id, LICITACION_ID, null, OPERADOR_ID, false, false);
  }
}
