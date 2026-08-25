package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import org.junit.jupiter.api.Test;

/** A member holds what the entry published, reduced no further than its siblings reduce. */
class PublishedConsortiumMemberTest {

  @Test
  void answers_nothing_for_the_name_that_carried_only_whitespace() {
    assertThat(new PublishedConsortiumMember("  ", null).name()).isNull();
  }

  @Test
  void keeps_the_internal_spacing_the_published_name_carried() {
    PublishedConsortiumMember member =
        new PublishedConsortiumMember("PRACE  SERVICIOS", new FiscalIdentifier("A70319678"));

    assertThat(member.name()).isEqualTo("PRACE  SERVICIOS");
  }

  /**
   * A member the source named but did not identify is still a member. It costs one membership,
   * which is the direction every identifier judgement on this record fails in — a member resolved
   * to the wrong operador would corrupt the catalogue instead.
   */
  @Test
  void holds_the_member_whose_entry_published_no_identifier() {
    PublishedConsortiumMember member = new PublishedConsortiumMember("PRACE", null);

    assertThat(member.fiscalIdentifier()).isNull();
    assertThat(member.name()).isEqualTo("PRACE");
  }
}
