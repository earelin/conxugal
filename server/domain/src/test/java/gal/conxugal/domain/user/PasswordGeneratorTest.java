package gal.conxugal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {

  private final PasswordGenerator passwordGenerator = new PasswordGenerator();

  @Test
  void generates_password_of_at_least_sixteen_characters() {
    String value = passwordGenerator.generate().value();

    assertThat(value.length()).isGreaterThanOrEqualTo(16);
  }

  @Test
  void generates_password_mixing_uppercase_lowercase_digits_and_symbols() {
    String value = passwordGenerator.generate().value();

    assertThat(value).containsPattern("[A-Z]");
    assertThat(value).containsPattern("[a-z]");
    assertThat(value).containsPattern("[0-9]");
    assertThat(value).containsPattern("[^A-Za-z0-9]");
  }

  @Test
  void generates_different_passwords_on_successive_calls() {
    String first = passwordGenerator.generate().value();
    String second = passwordGenerator.generate().value();

    assertThat(first).isNotEqualTo(second);
  }
}
