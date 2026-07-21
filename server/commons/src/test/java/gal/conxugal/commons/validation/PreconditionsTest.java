package gal.conxugal.commons.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PreconditionsTest {

  @Test
  void accepts_positive_value() {
    assertThatCode(() -> Preconditions.requireNotNegative(1L, "figure")).doesNotThrowAnyException();
  }

  @Test
  void accepts_zero() {
    assertThatCode(() -> Preconditions.requireNotNegative(0L, "figure")).doesNotThrowAnyException();
  }

  @Test
  void rejects_negative_value() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Preconditions.requireNotNegative(-1L, "figure"))
        .withMessageContaining("figure")
        .withMessageContaining("-1");
  }
}
