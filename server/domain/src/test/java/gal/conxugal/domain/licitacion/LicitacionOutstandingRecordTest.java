package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class LicitacionOutstandingRecordTest {

  private static final PublicationId PUBLICATION = new PublicationId("822054");

  // The four values a retried record cannot answer for itself, held beside the identifier so that
  // the procedure can be stored with no listing entry in hand.
  @Test
  void holds_the_four_values_the_listing_published_beside_the_identifier() {
    LicitacionOutstandingRecord outstanding =
        new LicitacionOutstandingRecord(
            PUBLICATION, LocalDate.of(2024, 3, 8), LocalDate.of(2026, 8, 20), 8, "Formalizado");

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(outstanding.publicationId()).isEqualTo(PUBLICATION);
      softly.assertThat(outstanding.publicationDate()).isEqualTo(LocalDate.of(2024, 3, 8));
      softly.assertThat(outstanding.lastModified()).isEqualTo(LocalDate.of(2026, 8, 20));
      softly.assertThat(outstanding.stateCode()).isEqualTo(8);
      softly.assertThat(outstanding.stateLabel()).isEqualTo("Formalizado");
    });
  }

  // A date the listing published in a form nothing could interpret is absent rather than fatal.
  @Test
  void holds_no_date_where_the_listing_published_none() {
    LicitacionOutstandingRecord outstanding =
        new LicitacionOutstandingRecord(PUBLICATION, null, null, 8, "Formalizado");

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(outstanding.publicationDate()).isNull();
      softly.assertThat(outstanding.lastModified()).isNull();
    });
  }

  // The label is held as the state vocabulary holds it, so the state a retry upserts from these
  // two values is the one the listing path would have upserted.
  @Test
  void blank_label_is_held_as_no_label_at_all() {
    LicitacionOutstandingRecord outstanding =
        new LicitacionOutstandingRecord(PUBLICATION, null, null, 8, "   ");

    assertThat(outstanding.stateLabel()).isNull();
  }

  // The entry belongs to whichever Órgano the ledger files it under, so two saying the same thing
  // about the same publication are one entry.
  @Test
  void two_entries_naming_the_same_publication_alike_are_equal() {
    assertThat(new LicitacionOutstandingRecord(PUBLICATION, null, null, 8, "Formalizado"))
        .isEqualTo(new LicitacionOutstandingRecord(PUBLICATION, null, null, 8, "Formalizado"));
  }

  @Test
  void refuses_an_entry_that_names_no_publication() {
    assertThatThrownBy(() -> new LicitacionOutstandingRecord(null, null, null, 8, "Formalizado"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("publicationId");
  }
}
