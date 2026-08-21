package gal.conxugal.commons.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DatesTest {

  private static final LocalDate EARLIER = LocalDate.of(2018, 1, 1);
  private static final LocalDate LATER = LocalDate.of(2026, 8, 6);

  @Test
  void answers_the_later_date_whichever_side_it_is_given_on() {
    assertThat(Dates.latest(EARLIER, LATER)).isEqualTo(LATER);
    assertThat(Dates.latest(LATER, EARLIER)).isEqualTo(LATER);
  }

  @Test
  void answers_the_earlier_date_whichever_side_it_is_given_on() {
    assertThat(Dates.earliest(EARLIER, LATER)).isEqualTo(EARLIER);
    assertThat(Dates.earliest(LATER, EARLIER)).isEqualTo(EARLIER);
  }

  @Test
  void answers_the_date_itself_when_both_are_the_same_day() {
    assertThat(Dates.latest(LATER, LocalDate.of(2026, 8, 6))).isEqualTo(LATER);
    assertThat(Dates.earliest(LATER, LocalDate.of(2026, 8, 6))).isEqualTo(LATER);
  }

  @Test
  void refuses_either_side_missing() {
    assertThatNullPointerException().isThrownBy(() -> Dates.latest(null, LATER));
    assertThatNullPointerException().isThrownBy(() -> Dates.latest(LATER, null));
    assertThatNullPointerException().isThrownBy(() -> Dates.earliest(null, LATER));
    assertThatNullPointerException().isThrownBy(() -> Dates.earliest(LATER, null));
  }
}
