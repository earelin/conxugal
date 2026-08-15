package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContratosMenoresSectionTest {

  private static final YearSelection LAST_YEAR = YearSelection.of(2024);
  private static final YearSelection THIS_YEAR = YearSelection.of(2025);

  // An empty section is the one state the rule "once present it is never empty" forbids, and it is
  // forbidden here rather than only at the use case so that no later producer can reintroduce it.
  @Test
  void refuses_the_section_that_offers_no_year_at_all() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ContratosMenoresSection(List.of(), false, false))
        .withMessageContaining("at least one year");
  }

  @Test
  void keeps_the_order_the_years_arrived_in() {
    ContratosMenoresSection section =
        new ContratosMenoresSection(List.of(THIS_YEAR, LAST_YEAR), false, false);

    assertThat(section.years()).containsExactly(THIS_YEAR, LAST_YEAR);
  }

  @Test
  void does_not_share_the_list_it_was_built_from() {
    List<YearSelection> offered = new ArrayList<>(List.of(THIS_YEAR));
    ContratosMenoresSection section = new ContratosMenoresSection(offered, false, false);

    offered.add(LAST_YEAR);

    assertThat(section.years()).containsExactly(THIS_YEAR);
  }

  // The state that proves the two flags are independent: an Órgano unmarked halfway through its
  // initial import is partial and no longer being refreshed at once, which one enum would have to
  // lie about.
  @Test
  void expresses_partial_without_updating() {
    ContratosMenoresSection section = new ContratosMenoresSection(List.of(THIS_YEAR), true, false);

    assertThat(section.partial()).isTrue();
    assertThat(section.updating()).isFalse();
  }

  @Test
  void expresses_partial_and_updating_together() {
    ContratosMenoresSection section = new ContratosMenoresSection(List.of(THIS_YEAR), true, true);

    assertThat(section.partial()).isTrue();
    assertThat(section.updating()).isTrue();
  }
}
