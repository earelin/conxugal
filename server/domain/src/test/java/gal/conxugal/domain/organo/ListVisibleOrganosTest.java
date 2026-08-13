package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Two families throughout, because one would let a use case that asked only the first still pass
// every assertion here — and asking every family is what makes "of any family" a rule rather than
// an aspiration.
@ExtendWith(MockitoExtension.class)
class ListVisibleOrganosTest {

  private static final OrganoId MAR = new OrganoId(UUID.randomUUID());
  private static final OrganoId SANIDADE = new OrganoId(UUID.randomUUID());
  private static final OrganoId FACENDA = new OrganoId(UUID.randomUUID());

  @Mock
  private OrganoRepository organoRepository;

  @Mock
  private OrganosWithVisibleContracts contratosMenores;

  @Mock
  private OrganosWithVisibleContracts licitacions;

  private ListVisibleOrganos listVisibleOrganos;

  @BeforeEach
  void setUp() {
    listVisibleOrganos =
        new ListVisibleOrganos(organoRepository, List.of(contratosMenores, licitacions));
  }

  @Test
  void includes_an_organo_whichever_family_answers_for_it() {
    OrganoDeContratacion mar = organo(MAR, "mar", "Consellería do Mar");
    OrganoDeContratacion sanidade = organo(SANIDADE, "sanidade", "Consellería de Sanidade");
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(mar, sanidade));
    when(contratosMenores.among(List.of(MAR, SANIDADE))).thenReturn(Set.of(MAR));
    when(licitacions.among(List.of(MAR, SANIDADE))).thenReturn(Set.of(SANIDADE));

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).containsExactly(mar, sanidade);
  }

  @Test
  void withholds_an_organo_no_family_answers_for() {
    OrganoDeContratacion mar = organo(MAR, "mar", "Consellería do Mar");
    OrganoDeContratacion sanidade = organo(SANIDADE, "sanidade", "Consellería de Sanidade");
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(mar, sanidade));
    when(contratosMenores.among(List.of(MAR, SANIDADE))).thenReturn(Set.of(MAR));
    when(licitacions.among(List.of(MAR, SANIDADE))).thenReturn(Set.of());

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).containsExactly(mar);
  }

  // The families answer sets, which carry no order of their own, and here both answer out of
  // catalogue order. The result must still be the repository's order, because that is the Galician
  // collation the read guarantees and nothing downstream re-sorts.
  @Test
  void keeps_the_catalogue_order_the_repository_delivered() {
    OrganoDeContratacion mar = organo(MAR, "mar", "Consellería do Mar");
    OrganoDeContratacion sanidade = organo(SANIDADE, "sanidade", "Consellería de Sanidade");
    OrganoDeContratacion facenda = organo(FACENDA, "facenda", "Consellería de Facenda");
    List<OrganoId> candidates = List.of(MAR, SANIDADE, FACENDA);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(mar, sanidade, facenda));
    when(contratosMenores.among(candidates)).thenReturn(orderedSet(FACENDA, MAR));
    when(licitacions.among(candidates)).thenReturn(orderedSet(SANIDADE, FACENDA));

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).containsExactly(mar, sanidade, facenda);
  }

  @Test
  void answers_nothing_when_no_family_holds_any_visible_contract() {
    when(organoRepository.findAllOrderByName())
        .thenReturn(List.of(organo(MAR, "mar", "Consellería do Mar")));
    when(contratosMenores.among(List.of(MAR))).thenReturn(Set.of());
    when(licitacions.among(List.of(MAR))).thenReturn(Set.of());

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).isEmpty();
  }

  // Stubbing cannot express "was never asked", and asking about nothing is one round trip per
  // family for an answer that is empty by construction.
  @Test
  void asks_no_family_about_an_empty_catalogue() {
    when(organoRepository.findAllOrderByName()).thenReturn(List.of());

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).isEmpty();
    verifyNoInteractions(contratosMenores, licitacions);
  }

  // Inactive and unclassified are facts about the catalogue row, not about whether there is
  // anything to read there, so neither is a reason to withhold an Órgano a family answered for.
  @Test
  void returns_an_inactive_or_unclassified_organo_like_any_other() {
    OrganoDeContratacion inactive =
        new OrganoDeContratacion(MAR, "mar", "Consellería do Mar", false, false, null);
    OrganoDeContratacion classified =
        new OrganoDeContratacion(
            SANIDADE, "sanidade", "Consellería de Sanidade", true, false,
            new TermoId(UUID.randomUUID()));
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(inactive, classified));
    when(contratosMenores.among(List.of(MAR, SANIDADE))).thenReturn(Set.of(MAR, SANIDADE));
    when(licitacions.among(List.of(MAR, SANIDADE))).thenReturn(Set.of());

    List<OrganoDeContratacion> result = listVisibleOrganos.list();

    assertThat(result).containsExactly(inactive, classified);
  }

  private static Set<OrganoId> orderedSet(OrganoId... ids) {
    return new LinkedHashSet<>(List.of(ids));
  }

  private static OrganoDeContratacion organo(OrganoId id, String sourceKey, String name) {
    return new OrganoDeContratacion(id, sourceKey, name, true, false, null);
  }
}
