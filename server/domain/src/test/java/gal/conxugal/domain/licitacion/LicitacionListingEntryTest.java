package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LicitacionListingEntryTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  @Test
  void rejects_null_publication_id() {
    assertThatNullPointerException()
        .isThrownBy(() -> entry(null, "Obxecto", "Formalizado"))
        .withMessageContaining("publicationId");
  }

  /**
   * Null rather than an empty string, so that everything above the port sees one absent case —
   * the rule {@link LicitacionOutstandingRecord} holds the same values under.
   */
  @Test
  void holds_text_that_published_nothing_as_absent() {
    LicitacionListingEntry entry = entry(PUBLICATION_ID, "   ", "");

    assertThat(entry)
        .extracting(LicitacionListingEntry::obxecto, LicitacionListingEntry::stateLabel)
        .containsExactly(null, null);
  }

  /** The state is the code and the label together, and only the label may be absent. */
  @Test
  void keeps_the_state_code_beside_the_label_it_shares_with_another_code() {
    LicitacionListingEntry entry =
        new LicitacionListingEntry(PUBLICATION_ID, null, null, null, null, 102, "Histórico");

    assertThat(entry)
        .extracting(LicitacionListingEntry::stateCode, LicitacionListingEntry::stateLabel)
        .containsExactly(102, "Histórico");
  }

  /** A procedure the source published nothing else about is still an entry. */
  @Test
  void accepts_an_entry_carrying_only_its_identifier_and_state() {
    LicitacionListingEntry entry =
        new LicitacionListingEntry(PUBLICATION_ID, null, null, null, null, 1, null);

    assertThat(entry.publicationId()).isEqualTo(PUBLICATION_ID);
  }

  @Test
  void carries_the_published_budget_at_the_scale_it_was_published_with() {
    LicitacionListingEntry entry = entry(PUBLICATION_ID, "Obxecto", "Formalizado");

    assertThat(entry.baseBudget()).isEqualTo(new Money(new BigDecimal("3378552.09")));
  }

  private static LicitacionListingEntry entry(
      PublicationId publicationId, String obxecto, String stateLabel) {
    return new LicitacionListingEntry(
        publicationId,
        LocalDate.of(2024, 3, 8),
        LocalDate.of(2026, 8, 20),
        obxecto,
        new Money(new BigDecimal("3378552.09")),
        8,
        stateLabel);
  }
}
