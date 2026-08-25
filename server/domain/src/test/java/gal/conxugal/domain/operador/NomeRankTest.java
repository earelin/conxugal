package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NomeRankTest {

  private static final FiscalIdentifier FISCAL_ID = new FiscalIdentifier("B12345678");

  private static final NomeRank EARLIER = new NomeRank(LocalDate.of(2025, 1, 9), 900L);
  private static final NomeRank LATER = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

  @Test
  void later_publication_date_outranks_an_earlier_one() {
    assertThat(LATER.outranks(EARLIER)).isTrue();
  }

  @Test
  void earlier_publication_date_never_outranks_the_later_one() {
    assertThat(EARLIER.outranks(LATER)).isFalse();
  }

  @Test
  void date_tie_is_settled_on_the_higher_source_identifier() {
    NomeRank higher = new NomeRank(LocalDate.of(2026, 3, 14), 5100L);
    NomeRank lower = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

    assertThat(higher.outranks(lower)).isTrue();
    assertThat(lower.outranks(higher)).isFalse();
  }

  @Test
  void dated_contract_outranks_an_undated_one() {
    assertThat(EARLIER.outranks(new NomeRank(null, 17L))).isTrue();
  }

  @Test
  void undated_contract_never_outranks_the_dated_one_however_late_it_arrives() {
    NomeRank undated = new NomeRank(null, Long.MAX_VALUE);

    assertThat(undated.outranks(EARLIER)).isFalse();
  }

  @Test
  void two_undated_contracts_are_settled_on_the_higher_source_identifier() {
    NomeRank higher = new NomeRank(null, 5100L);
    NomeRank lower = new NomeRank(null, 900L);

    assertThat(higher.outranks(lower)).isTrue();
    assertThat(lower.outranks(higher)).isFalse();
  }

  @Test
  void rank_does_not_outrank_itself() {
    assertThat(LATER.outranks(LATER)).isFalse();
  }

  @Test
  void two_readings_of_one_contract_do_not_outrank_each_other() {
    NomeRank read = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
    NomeRank reread = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

    assertThat(read.outranks(reread)).isFalse();
    assertThat(reread.outranks(read)).isFalse();
  }

  /** The winner is the greatest, not the first — undated sorts ahead of everything it loses to. */
  @Test
  void sorting_puts_the_undated_rank_first_and_the_winner_last() {
    NomeRank undated = new NomeRank(null, Long.MAX_VALUE);
    List<NomeRank> ranks = new ArrayList<>(List.of(LATER, undated, EARLIER));

    Collections.sort(ranks);

    assertThat(ranks).containsExactly(undated, EARLIER, LATER);
    assertThat(Collections.max(ranks)).isEqualTo(LATER);
  }

  /**
   * The rank a publication that ranks nothing catalogues an operador at. Everything below is one
   * property: it loses to every rank a real publication can carry. If it ever stopped losing, a
   * bid would decide the displayed name, which is the whole reason it exists.
   */
  @Test
  void every_dated_rank_outranks_the_unranked_one() {
    assertThat(EARLIER.outranks(NomeRank.unranked())).isTrue();
    assertThat(new NomeRank(LocalDate.MIN, Long.MIN_VALUE).outranks(NomeRank.unranked())).isTrue();
  }

  // Undated ranks last among real publications, and still ahead of this one — so an operador a bid
  // catalogued takes its name from the first contract to name it, dated or not.
  @Test
  void undated_rank_of_any_real_publication_outranks_the_unranked_one() {
    assertThat(new NomeRank(null, Long.MIN_VALUE + 1).outranks(NomeRank.unranked())).isTrue();
    assertThat(new NomeRank(null, 0L).outranks(NomeRank.unranked())).isTrue();
  }

  @Test
  void unranked_rank_outranks_nothing_not_even_itself() {
    assertThat(NomeRank.unranked().outranks(EARLIER)).isFalse();
    assertThat(NomeRank.unranked().outranks(new NomeRank(null, Long.MIN_VALUE + 1))).isFalse();
    assertThat(NomeRank.unranked().outranks(NomeRank.unranked())).isFalse();
  }

  @Test
  void sorting_puts_the_unranked_rank_ahead_of_every_real_rank() {
    NomeRank undated = new NomeRank(null, Long.MAX_VALUE);
    List<NomeRank> ranks =
        new ArrayList<>(List.of(LATER, undated, EARLIER, NomeRank.unranked()));

    Collections.sort(ranks);

    assertThat(ranks).containsExactly(NomeRank.unranked(), undated, EARLIER, LATER);
  }

  // It is compared by value rather than by identity wherever it is read back off a stored row,
  // where nothing this factory returned survives the round trip.
  @Test
  void unranked_is_the_same_rank_however_it_was_built() {
    assertThat(NomeRank.unranked()).isEqualTo(NomeRank.unranked());
    assertThat(NomeRank.unranked()).isEqualTo(new NomeRank(null, Long.MIN_VALUE));
  }

  @Test
  void unranked_rank_ranks_nothing() {
    assertThat(NomeRank.unranked().ranksNothing()).isTrue();
    assertThat(new NomeRank(null, Long.MIN_VALUE).ranksNothing()).isTrue();
  }

  /**
   * The near miss is the case that matters: an undated contract one step above the sentinel is a
   * real publication, and answering true for it would drop a name R15 retains — silently, since
   * no port drops a retained name except as a side effect of promoting it.
   */
  @Test
  void undated_rank_one_above_the_sentinel_ranks_something() {
    assertThat(new NomeRank(null, Long.MIN_VALUE + 1).ranksNothing()).isFalse();
  }

  // The other near miss: the sentinel is the pair, not the source identifier on its own.
  @Test
  void dated_rank_at_the_least_source_identifier_ranks_something() {
    assertThat(new NomeRank(LocalDate.of(2026, 3, 14), Long.MIN_VALUE).ranksNothing()).isFalse();
  }

  @Test
  void ordinary_rank_ranks_something() {
    assertThat(EARLIER.ranksNothing()).isFalse();
    assertThat(LATER.ranksNothing()).isFalse();
    assertThat(new NomeRank(null, 17L).ranksNothing()).isFalse();
  }

  @Test
  void ranks_that_compare_equal_are_equal() {
    NomeRank read = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
    NomeRank reread = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

    assertThat(read.compareTo(reread)).isZero();
    assertThat(read).isEqualTo(reread);
  }

  @Test
  void ranks_that_differ_never_compare_equal() {
    assertThat(LATER.compareTo(EARLIER)).isPositive();
    assertThat(EARLIER.compareTo(LATER)).isNegative();
    assertThat(LATER).isNotEqualTo(EARLIER);
  }

  @Test
  void displayed_name_outranks_the_name_retained_at_its_date_and_lower_source_identifier() {
    NomeRank sharedDateLowerSourceId = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval", false,
            new NomeRank(LocalDate.of(2026, 3, 14), 5100L),
            Set.of(
                new NomeAlternativo(
                    null, "Obradoiro Naval, S.L.", sharedDateLowerSourceId)));
    NomeAlternativo retained = operador.nomesAlternativos().iterator().next();

    assertThat(operador.nameRank().outranks(retained.lastPublished())).isTrue();
  }

  @Test
  void displayed_name_outranks_the_name_retained_from_an_undated_contract() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval", false,
            EARLIER,
            Set.of(
                new NomeAlternativo(null, "Obradoiro Naval, S.L.", new NomeRank(null, 5100L))));
    NomeAlternativo retained = operador.nomesAlternativos().iterator().next();

    assertThat(operador.nameRank().outranks(retained.lastPublished())).isTrue();
  }
}
