package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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

  @Test
  void carries_every_column_its_table_holds_and_nothing_besides() {
    assertThat(
            Arrays.stream(Participation.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "id",
            "licitacionId",
            "loteId",
            "operadorEconomicoId",
            "won",
            "consortium",
            "consortiumName",
            "withdrawn");
  }

  @Test
  void constructs_as_single_firm_the_catalogue_holds() {
    // 578 of 613 measured bidder rows, every one carrying an ordinary fiscal identifier.
    Participation firm = singleFirm(OPERADOR_ID);

    assertThat(firm.operadorEconomicoId()).isEqualTo(OPERADOR_ID);
    assertThat(firm.consortium()).isFalse();
    assertThat(firm.consortiumName()).isNull();
  }

  @Test
  void constructs_as_single_firm_the_catalogue_could_not_hold() {
    // The row whose published identifier was unusable: recorded as neither participant nor
    // awardee of any catalogue entry, rather than dropped.
    Participation unidentifiedFirm = singleFirm(null);

    assertThat(unidentifiedFirm.operadorEconomicoId()).isNull();
    assertThat(unidentifiedFirm.consortium()).isFalse();
    assertThat(unidentifiedFirm.consortiumName()).isNull();
  }

  @Test
  void constructs_as_consortium_the_source_identified() {
    // 2 of 35 measured consortia carry a genuine identifier of their own, so the UTE is an
    // operador and no name of its own is held.
    Participation identifiedUte =
        new Participation(LICITACION_ID, LOTE_ID, OPERADOR_ID, true, true, null);

    assertThat(identifiedUte.operadorEconomicoId()).isEqualTo(OPERADOR_ID);
    assertThat(identifiedUte.consortium()).isTrue();
    assertThat(identifiedUte.consortiumName()).isNull();
  }

  @Test
  void constructs_as_consortium_the_source_did_not_identify() {
    // The other 33 of 35, carrying a dash or a placeholder. Its published name is the one name
    // this family holds of its own, because no operador can carry it.
    Participation unidentifiedUte = unidentifiedConsortium();

    assertThat(unidentifiedUte.operadorEconomicoId()).isNull();
    assertThat(unidentifiedUte.consortium()).isTrue();
    assertThat(unidentifiedUte.consortiumName()).isEqualTo("UTE PRACE-TABOADA RAMOS");
  }

  @Test
  void carries_the_consortium_name_the_source_published_however_it_is_spelled() {
    // 7 of 35 consortia are published under a name that does not begin with UTE, so nothing here
    // reads the name to decide what the row is.
    assertThat(
            new Participation(
                    LICITACION_ID, null, null, false, true, "MISTURAS-INGESAN")
                .consortiumName())
        .isEqualTo("MISTURAS-INGESAN");
  }

  @Test
  void constructs_against_the_procedure_as_whole_when_it_has_no_lotes() {
    assertThat(singleFirm(OPERADOR_ID).loteId())
        .isNull();
  }

  @Test
  void marks_the_bid_that_won_so_one_operador_can_win_lote_and_lose_another() {
    Participation winner = new Participation(LICITACION_ID, LOTE_ID, OPERADOR_ID, true, false,
        null);
    Participation loser = new Participation(LICITACION_ID, null, OPERADOR_ID, false, false, null);

    assertThat(winner.won()).isTrue();
    assertThat(loser.won()).isFalse();
  }

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    assertThat(singleFirm(OPERADOR_ID).id())
        .isNull();
  }

  @Test
  void is_born_visible_so_nothing_an_import_stores_is_invisible() {
    assertThat(singleFirm(OPERADOR_ID).withdrawn())
        .isFalse();
  }

  @Test
  void holds_the_consortium_name_that_published_only_whitespace_as_null() {
    // Nothing measured guarantees a consortium's cell carries a name, and a blank one is not one.
    assertThat(new Participation(LICITACION_ID, null, null, false, true, " \t").consortiumName())
        .isNull();
  }

  @Test
  void requires_the_procedure_it_belongs_to() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Participation(null, null, OPERADOR_ID, false, false, null));
  }

  @Test
  void refuses_the_published_name_beside_an_operador_the_catalogue_holds() {
    // The name is the exception for a party no operador can carry, so one carrying both would be
    // a second name for a party that already has one. Refused where the mistake is, rather than
    // at the insert, where it would cost the whole procedure.
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Participation(
                    LICITACION_ID, null, OPERADOR_ID, true, true, "UTE PRACE-TABOADA RAMOS"));
  }

  @Test
  void refuses_the_published_name_on_the_bid_that_was_not_consortium() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new Participation(LICITACION_ID, null, null, false, false, "MISTURAS-INGESAN"));
  }

  @Test
  void accepts_the_consortium_that_published_no_name_at_all() {
    // The refusal is one-directional: nothing measured guarantees a consortium's cell carries a
    // name, and refusing that row would lose a real bid.
    Participation nameless = new Participation(LICITACION_ID, LOTE_ID, null, false, true, null);

    assertThat(nameless.consortium()).isTrue();
    assertThat(nameless.consortiumName()).isNull();
  }

  @Test
  void is_the_same_participation_as_itself_whether_stored_or_not() {
    Participation identified = stored(new ParticipationId(UUID.randomUUID()));
    Participation sameIdentified = identified;
    Participation unstored = singleFirm(OPERADOR_ID);
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

  @Test
  void stays_the_same_bid_when_the_consortium_it_made_becomes_identified() {
    // Identifying a consortium sets its operador and clears its published name in one statement,
    // and it is the same bid throughout.
    ParticipationId id = new ParticipationId(UUID.randomUUID());

    Participation beforeIdentifying =
        new Participation(id, LICITACION_ID, LOTE_ID, null, true, true,
            "UTE PRACE-TABOADA RAMOS", false);
    Participation afterIdentifying =
        new Participation(id, LICITACION_ID, LOTE_ID, OPERADOR_ID, true, true, null, false);

    assertThat(beforeIdentifying)
        .isEqualTo(afterIdentifying)
        .hasSameHashCodeAs(afterIdentifying);
  }

  @Test
  void treats_participations_the_database_has_not_identified_as_distinct() {
    assertThat(new HashSet<>(List.of(singleFirm(OPERADOR_ID), singleFirm(OPERADOR_ID))))
        .hasSize(2);
  }

  @Test
  void hashes_consistently_while_it_carries_no_identity() {
    Participation unstored = singleFirm(OPERADOR_ID);
    int firstReading = unstored.hashCode();

    assertThat(unstored.hashCode()).isEqualTo(firstReading);
  }

  private static Participation singleFirm(OperadorId operadorId) {
    return new Participation(LICITACION_ID, null, operadorId, false, false, null);
  }

  private static Participation unidentifiedConsortium() {
    return new Participation(
        LICITACION_ID, LOTE_ID, null, true, true, "UTE PRACE-TABOADA RAMOS");
  }

  private static Participation stored(ParticipationId id) {
    return new Participation(id, LICITACION_ID, null, OPERADOR_ID, false, false, null, false);
  }
}
