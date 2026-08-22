package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class PublicationIdTest {

  @Test
  void rejects_null_value() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PublicationId(null));
  }

  @Test
  void refuses_the_blank_identifier_that_would_collapse_every_procedure_onto_one_row() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PublicationId(" \t"));
  }

  @Test
  void refuses_an_untrimmed_identifier_that_would_import_the_procedure_twice() {
    // The natural key, so padding the adapter failed to strip would key a second row for one
    // published procedure rather than matching the one already stored.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PublicationId("  822054  "));
  }

  @Test
  void carries_an_identifier_the_source_did_not_mint_as_number() {
    // Nothing about the shape is this type's business: it holds what was published, so a source
    // that started issuing alphanumeric identifiers costs a parse at the adapter and no more.
    assertThat(new PublicationId("LIC-2026/0042").value())
        .isEqualTo("LIC-2026/0042");
  }

  @Test
  void keeps_the_identifier_exactly_as_published() {
    assertThat(new PublicationId("822054").value())
        .isEqualTo("822054");
  }

  @Test
  void separates_two_identifiers_that_differ_only_by_leading_zeros() {
    // Nothing normalises the value, so the source's own spelling is what matches. Were these one
    // publication, the source would have published one identifier for it.
    assertThat(new PublicationId("0822054"))
        .isNotEqualTo(new PublicationId("822054"));
  }

  @Test
  void is_the_same_identifier_as_another_reading_of_the_same_published_value() {
    assertThat(new PublicationId("822054"))
        .isEqualTo(new PublicationId("822054"))
        .hasSameHashCodeAs(new PublicationId("822054"));
  }

  @Test
  void prints_as_the_bare_identifier_so_messages_read_unchanged() {
    assertThat(new PublicationId("822054")).hasToString("822054");
  }
}
