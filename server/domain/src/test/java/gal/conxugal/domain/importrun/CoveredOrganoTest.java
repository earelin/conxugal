package gal.conxugal.domain.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.organo.OrganoId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pair a claim enumerates, and the equality the claim leans on.
 *
 * <p>A claim collects what it was asked to cover into a set before it inserts anything, so what
 * counts as the same pair decides how many coverage rows a run gets. Both halves of that matter and
 * both are silent when wrong: collapsing too much loses a family the trigger asked for, and
 * collapsing too little rolls the whole claim back on the composite key — taking the import guard
 * with it.
 */
class CoveredOrganoTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());

  @Test
  void rejects_the_pair_that_names_no_organo() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CoveredOrgano(null, ContractFamily.CONTRATOS_MENORES))
        .withMessageContaining("organoId");
  }

  @Test
  void rejects_the_pair_that_names_no_family() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CoveredOrgano(ORGANO_ID, null))
        .withMessageContaining("family");
  }

  // Naming one twice is a caller's slip rather than a run that covers it twice, and the claim
  // answers it by collecting into a set rather than by refusing.
  @Test
  void the_same_organo_named_twice_for_one_family_is_one_pair() {
    assertThat(deduped(contratosMenores(), contratosMenores())).containsExactly(contratosMenores());
  }

  /**
   * The other half, and the one this type exists for: two families of one Órgano are two pairs, so
   * the set a claim dedupes through keeps both and the run gets a row for each. Were they equal —
   * a pair identified by its Órgano alone — a trigger asking for both families would silently claim
   * a run covering one, which is exactly what the shipped two-column key used to do.
   */
  @Test
  void the_two_families_of_one_organo_are_two_pairs() {
    assertThat(deduped(contratosMenores(), licitacions()))
        .containsExactly(contratosMenores(), licitacions());
  }

  /**
   * As {@code claim} does it: a {@link LinkedHashSet}, so the coverage keeps the order asked for.
   */
  private static Set<CoveredOrgano> deduped(CoveredOrgano... covered) {
    return new LinkedHashSet<>(List.of(covered));
  }

  private static CoveredOrgano contratosMenores() {
    return new CoveredOrgano(ORGANO_ID, ContractFamily.CONTRATOS_MENORES);
  }

  private static CoveredOrgano licitacions() {
    return new CoveredOrgano(ORGANO_ID, ContractFamily.LICITACIONS);
  }
}
