package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperadorEconomicoTest {

  private static final FiscalIdentifier FISCAL_ID = new FiscalIdentifier("B12345678");

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
  private static final NomeRank NEWER = new NomeRank(LocalDate.of(2026, 7, 1), 5100L);
  private static final NomeRank OLDER = new NomeRank(LocalDate.of(2025, 1, 9), 900L);

  @Test
  void the_identity_is_distinct_from_the_fiscal_identifier() {
    OperadorId id = new OperadorId(UUID.randomUUID());
    OperadorEconomico operador =
        new OperadorEconomico(id, FISCAL_ID, "Obradoiro Naval", RANK, Set.of());

    assertThat(operador.id()).isEqualTo(id);
    assertThat(operador.fiscalId()).isEqualTo(FISCAL_ID);
  }

  @Test
  void keeps_the_name_exactly_as_published() {
    OperadorEconomico operador =
        new OperadorEconomico(FISCAL_ID, "  obradoiro   Naval, S.L. ", RANK);

    assertThat(operador.name()).isEqualTo("  obradoiro   Naval, S.L. ");
  }

  @Test
  void carries_the_rank_of_the_contract_the_name_came_from() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    assertThat(operador.nameRank()).isEqualTo(RANK);
  }

  @Test
  void allows_null_id_before_being_persisted() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    assertThat(operador.id()).isNull();
  }

  @Test
  void newly_catalogued_operador_has_borne_no_other_name() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    assertThat(operador.nomesAlternativos()).isEmpty();
  }

  @Test
  void retains_every_distinct_name_its_contracts_have_published() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            RANK,
            Set.of(
                new NomeAlternativo(null, "Obradoiro Naval, S.L.", OLDER),
                new NomeAlternativo(null, "Obradoiro Naval SL", NEWER)));

    assertThat(operador.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactlyInAnyOrder("Obradoiro Naval, S.L.", "Obradoiro Naval SL");
  }

  @Test
  void rejects_null_fiscal_id() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico(null, "Obradoiro Naval", RANK));
  }

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico(FISCAL_ID, null, RANK));
  }

  @Test
  void rejects_null_name_rank() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", null));
  }

  @Test
  void rejects_null_nomes_alternativos() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new OperadorEconomico(null, FISCAL_ID, "Obradoiro Naval", RANK, null));
  }

  @Test
  void rejects_an_alternative_equal_to_the_principal_name() {
    Set<NomeAlternativo> alternativos =
        Set.of(new NomeAlternativo(null, "Obradoiro Naval", RANK));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OperadorEconomico(
                    null, FISCAL_ID, "Obradoiro Naval", RANK, alternativos));
  }

  /**
   * Which of the two ranks survives is not pinned here because it is not decided here: the names
   * are one value, so whichever is built second never enters the set. Deciding the rank is the
   * caller's, before it builds the set.
   */
  @Test
  void the_same_name_at_two_ranks_is_retained_once() {
    Set<NomeAlternativo> alternativos =
        new HashSet<>(
            List.of(
                new NomeAlternativo(null, "Obradoiro Naval, S.L.", OLDER),
                new NomeAlternativo(null, "Obradoiro Naval, S.L.", NEWER)));

    OperadorEconomico operador =
        new OperadorEconomico(null, FISCAL_ID, "Obradoiro Naval", RANK, alternativos);

    assertThat(operador.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactly("Obradoiro Naval, S.L.");
  }

  @Test
  void an_alternative_differing_from_the_principal_only_in_case_is_allowed() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo(null, "OBRADOIRO NAVAL", RANK)));

    assertThat(operador.nomesAlternativos()).hasSize(1);
  }

  @Test
  void hands_out_the_retained_names_as_an_unmodifiable_set() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo(null, "Obradoiro Naval, S.L.", OLDER)));

    assertThatThrownBy(() -> operador.nomesAlternativos().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void advancing_the_display_moves_the_name_and_the_rank_together() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(advanced.name()).isEqualTo("Obradoiro Naval, S.L.");
    assertThat(advanced.nameRank()).isEqualTo(NEWER);
  }

  @Test
  void advancing_the_display_retains_the_name_it_displaced_under_the_rank_it_won_with() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(advanced.nomesAlternativos())
        .extracting(NomeAlternativo::name, NomeAlternativo::lastPublished)
        .containsExactlyInAnyOrder(tuple("Obradoiro Naval", RANK));
  }

  @Test
  void the_name_it_displaced_is_retained_against_the_operador_it_belongs_to() {
    OperadorId id = new OperadorId(UUID.randomUUID());
    OperadorEconomico operador =
        new OperadorEconomico(id, FISCAL_ID, "Obradoiro Naval", RANK, Set.of());

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(advanced.nomesAlternativos())
        .extracting(NomeAlternativo::operadorEconomicoId)
        .containsExactly(id);
  }

  @Test
  void advancing_the_display_leaves_the_new_name_absent_from_the_alternatives() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo(null, "Obradoiro Naval, S.L.", OLDER)));

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(advanced.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactly("Obradoiro Naval");
  }

  @Test
  void advancing_the_display_keeps_the_identity_and_the_fiscal_identifier() {
    OperadorId id = new OperadorId(UUID.randomUUID());
    OperadorEconomico operador =
        new OperadorEconomico(id, FISCAL_ID, "Obradoiro Naval", RANK, Set.of());

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(advanced.id()).isEqualTo(id);
    assertThat(advanced.fiscalId()).isEqualTo(FISCAL_ID);
  }

  @Test
  void republishing_the_displayed_name_advances_the_rank_and_retains_nothing() {
    OperadorEconomico operador = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval", NEWER);

    assertThat(advanced.nameRank()).isEqualTo(NEWER);
    assertThat(advanced.nomesAlternativos()).isEmpty();
  }

  @Test
  void republishing_the_displayed_name_under_the_same_rank_changes_nothing() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo(null, "Obradoiro Naval, S.L.", OLDER)));

    OperadorEconomico republished = operador.displaying("Obradoiro Naval", RANK);

    assertThat(republished.name()).isEqualTo("Obradoiro Naval");
    assertThat(republished.nameRank()).isEqualTo(RANK);
    assertThat(republished.nomesAlternativos())
        .extracting(NomeAlternativo::name, NomeAlternativo::lastPublished)
        .containsExactlyInAnyOrder(tuple("Obradoiro Naval, S.L.", OLDER));
  }

  @Test
  void an_operador_displayed_under_another_name_is_still_the_same_operador() {
    OperadorId id = new OperadorId(UUID.randomUUID());
    OperadorEconomico before =
        new OperadorEconomico(id, FISCAL_ID, "Obradoiro Naval", RANK, Set.of());
    OperadorEconomico after = before.displaying("Obradoiro Naval, S.L.", NEWER);

    assertThat(before).isEqualTo(after);
    assertThat(before).hasSameHashCodeAs(after);
  }

  @Test
  void operadores_under_different_ids_are_different_operadores_whatever_their_fiscal_identifier() {
    OperadorEconomico one =
        new OperadorEconomico(
            new OperadorId(UUID.randomUUID()), FISCAL_ID, "Obradoiro Naval", RANK, Set.of());
    OperadorEconomico other =
        new OperadorEconomico(
            new OperadorId(UUID.randomUUID()), FISCAL_ID, "Obradoiro Naval", RANK, Set.of());

    assertThat(one).isNotEqualTo(other);
  }

  @Test
  void uncatalogued_operadores_are_equal_to_nothing_but_themselves() {
    OperadorEconomico one = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);
    OperadorEconomico other = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);

    assertThat(one).isNotEqualTo(other);
    assertThat(Set.of(one, other))
        .hasSize(2)
        .contains(one, other);
  }

  @Test
  void an_uncatalogued_operador_matches_no_catalogued_one_in_either_direction() {
    OperadorEconomico uncatalogued = new OperadorEconomico(FISCAL_ID, "Obradoiro Naval", RANK);
    OperadorEconomico catalogued =
        new OperadorEconomico(
            new OperadorId(UUID.randomUUID()), FISCAL_ID, "Obradoiro Naval", RANK, Set.of());

    assertThat(uncatalogued).isNotEqualTo(catalogued);
    assertThat(catalogued).isNotEqualTo(uncatalogued);
  }
}
