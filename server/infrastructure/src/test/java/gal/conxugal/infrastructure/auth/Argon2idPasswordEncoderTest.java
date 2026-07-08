package gal.conxugal.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class Argon2idPasswordEncoderTest {

  private final Argon2idPasswordEncoder passwordEncoder = new Argon2idPasswordEncoder();

  @Test
  void matches_the_original_password_against_its_own_encoding() {
    String encoded = passwordEncoder.encode("correct horse battery staple");

    assertThat(passwordEncoder.matches("correct horse battery staple", encoded)).isTrue();
  }

  @Test
  void rejects_wrong_password_against_an_encoding() {
    String encoded = passwordEncoder.encode("correct horse battery staple");

    assertThat(passwordEncoder.matches("wrong password", encoded)).isFalse();
  }

  @Test
  void encodes_the_same_password_differently_each_time() {
    String first = passwordEncoder.encode("correct horse battery staple");
    String second = passwordEncoder.encode("correct horse battery staple");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void never_encodes_the_password_as_plaintext() {
    String rawPassword = "correct horse battery staple";

    String encoded = passwordEncoder.encode(rawPassword);

    assertThat(encoded).doesNotContain(rawPassword);
  }

  @Test
  void match_against_dummy_hash_does_not_throw_for_any_input() {
    assertThatCode(() -> passwordEncoder.matchAgainstDummyHash("whatever"))
        .doesNotThrowAnyException();
  }
}
