package gal.conxugal.domain.licitacion;

import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.NAME_DERIVED;
import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.PUBLISHED_BY_BIDDER;
import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION;
import static gal.conxugal.domain.licitacion.AwardeeResolutionPath.UNRESOLVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AwardeeResolutionPathTest {

  @Test
  void orders_the_four_paths_weakest_first_so_the_greater_one_supersedes() {
    // The order is the type's own, not a convention a caller applies: sorting them answers which
    // supersedes which without anything else being consulted, and the strongest is the maximum.
    assertThat(
            List.of(PUBLISHED_BY_BIDDER, UNRESOLVED, PUBLISHED_BY_FORMALISATION, NAME_DERIVED)
                .stream()
                .sorted()
                .toList())
        .containsExactly(UNRESOLVED, NAME_DERIVED, PUBLISHED_BY_BIDDER, PUBLISHED_BY_FORMALISATION);
  }

  @Test
  void answers_which_supersedes_for_every_ordered_pair_of_the_four() {
    AwardeeResolutionPath[] weakestFirst = AwardeeResolutionPath.values();

    for (int stronger = 0; stronger < weakestFirst.length; stronger++) {
      for (int weaker = 0; weaker < stronger; weaker++) {
        assertThat(weakestFirst[stronger].supersedes(weakestFirst[weaker])).isTrue();
        assertThat(weakestFirst[weaker].supersedes(weakestFirst[stronger])).isFalse();
      }
    }
  }

  @Test
  void lets_the_formalisation_supersede_every_other_path() {
    assertThat(PUBLISHED_BY_FORMALISATION.supersedes(PUBLISHED_BY_BIDDER)).isTrue();
    assertThat(PUBLISHED_BY_FORMALISATION.supersedes(NAME_DERIVED)).isTrue();
    assertThat(PUBLISHED_BY_FORMALISATION.supersedes(UNRESOLVED)).isTrue();
  }

  @Test
  void lets_the_bidder_list_supersede_the_derived_and_the_unresolved_paths_only() {
    assertThat(PUBLISHED_BY_BIDDER.supersedes(NAME_DERIVED)).isTrue();
    assertThat(PUBLISHED_BY_BIDDER.supersedes(UNRESOLVED)).isTrue();
    assertThat(PUBLISHED_BY_BIDDER.supersedes(PUBLISHED_BY_FORMALISATION)).isFalse();
  }

  @Test
  void keeps_the_published_identifier_ahead_of_one_derived_from_the_name() {
    // The rule a reconciliation gates its re-resolution write on: a published identifier wins even
    // where that moves the award to a different operador.
    assertThat(NAME_DERIVED.supersedes(PUBLISHED_BY_BIDDER)).isFalse();
    assertThat(NAME_DERIVED.supersedes(PUBLISHED_BY_FORMALISATION)).isFalse();
    assertThat(NAME_DERIVED.supersedes(UNRESOLVED)).isTrue();
  }

  @Test
  void leaves_the_unresolved_path_superseding_nothing_at_all() {
    assertThat(UNRESOLVED.supersedes(NAME_DERIVED)).isFalse();
    assertThat(UNRESOLVED.supersedes(PUBLISHED_BY_BIDDER)).isFalse();
    assertThat(UNRESOLVED.supersedes(PUBLISHED_BY_FORMALISATION)).isFalse();
  }

  @Test
  void refuses_to_supersede_itself_so_the_replayed_resolution_cannot_make_an_awardee_flap() {
    for (AwardeeResolutionPath path : AwardeeResolutionPath.values()) {
      assertThat(path.supersedes(path)).isFalse();
    }
  }
}
