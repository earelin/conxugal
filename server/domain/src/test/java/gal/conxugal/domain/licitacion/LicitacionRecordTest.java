package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The source record's own guarantees, as opposed to what a parse puts in it: the tables are copied
 * on the way in, and the two values a record cannot be without are required.
 */
class LicitacionRecordTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");

  /**
   * A caller keeping the list it handed over must not be able to change what the record says
   * afterwards — every table here is read repeatedly by the store, and a record that could be
   * edited behind it would be a different procedure on the second read.
   */
  @Test
  void copies_the_tables_it_was_handed() {
    List<PublishedAward> awards = new ArrayList<>();
    awards.add(new PublishedAward("1", null, null, null, null, null, null));
    LicitacionRecord record = record(awards);

    awards.clear();

    assertThat(record.awards()).hasSize(1);
  }

  @Test
  void refuses_the_record_that_names_no_publication() {
    assertThatThrownBy(
            () ->
                new LicitacionRecord(
                    null, null, null, null, null, null, null, null, null, "Formalizado",
                    List.of(), List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  /** The one value the record is refused over, because a procedure without a state is not one. */
  @Test
  void refuses_the_record_that_publishes_no_state() {
    assertThatThrownBy(
            () ->
                new LicitacionRecord(
                    PUBLICATION_ID, null, null, null, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  /** A table the record does not publish is an empty list, which is an ordinary answer. */
  @Test
  void holds_the_record_that_publishes_none_of_its_tables() {
    LicitacionRecord record = record(List.of());

    assertThat(record.lotes()).isEmpty();
    assertThat(record.awards()).isEmpty();
    assertThat(record.formalisations()).isEmpty();
    assertThat(record.cpvClassifications()).isEmpty();
    assertThat(record.nutClassifications()).isEmpty();
  }

  private static LicitacionRecord record(List<PublishedAward> awards) {
    return new LicitacionRecord(
        PUBLICATION_ID,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "Formalizado",
        List.of(),
        awards,
        List.of(),
        List.of(),
        List.of());
  }
}
