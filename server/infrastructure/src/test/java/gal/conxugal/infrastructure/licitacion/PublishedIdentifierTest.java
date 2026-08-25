package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one judgement two cells on the record share, pinned against the tokens it was measured on.
 * Both cases matter in the same way: a token wrongly accepted catalogues a party under a word,
 * and a token wrongly rejected loses a real identifier.
 */
class PublishedIdentifierTest {

  /** Every identifier shape the captured records publish, including a UTE's own. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "A41111220",
        "B36746584",
        "U86486669",
        "A70319678",
        "B94181807",
        "12345678Z",
        "X1234567Z",
        "33545498K",
        "12345678"
      })
  void accepts_the_identifier_shapes_the_captured_records_publish(String token) {
    assertThat(PublishedIdentifier.isIdentifierShaped(token)).isTrue();
  }

  /**
   * Every name token measured beside one. {@code TEMP-2026-0001} is the placeholder the source
   * writes where it has none — it is 14 characters carrying 8 digits, so the hyphen is the only
   * thing that rejects it, and cataloguing an operador under it would pool unrelated suppliers
   * under one identity.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "S.A.",
        "S.L.",
        "MARTÍN",
        "TEMP-2026-0001",
        "TEMP-00934",
        "ABADIN",
        "SLU",
        "CONSTRUCCIONES",
        "GRUPO2000",
        "MISTURAS",
        "-",
        ""
      })
  void rejects_the_name_tokens_measured_beside_an_identifier(String token) {
    assertThat(PublishedIdentifier.isIdentifierShaped(token)).isFalse();
  }
}
