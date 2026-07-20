package gal.conxugal.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class GeneratedPasswordTest {

  @Test
  void exposes_the_plaintext_value() {
    GeneratedPassword password = new GeneratedPassword("Tg7#kLp2Qw9$mZxR");

    assertThat(password.value()).isEqualTo("Tg7#kLp2Qw9$mZxR");
  }

  @Test
  void rejects_null_value() {
    assertThatNullPointerException().isThrownBy(() -> new GeneratedPassword(null));
  }

  @Test
  void toString_redacts_the_value() {
    GeneratedPassword password = new GeneratedPassword("Tg7#kLp2Qw9$mZxR");

    assertThat(password.toString()).doesNotContain("Tg7#kLp2Qw9$mZxR");
  }
}
