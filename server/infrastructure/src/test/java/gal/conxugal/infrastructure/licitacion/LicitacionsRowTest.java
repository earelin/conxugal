package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionListingUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What one published listing row means, which is where the source's shape stops. */
class LicitacionsRowTest {

  private static final long PUBLICATION_ID = 822054L;

  /** The measured sample row, whose object is long and accented and whose amount has scale. */
  @Test
  void narrows_the_published_row_to_the_values_the_port_answers_with() {
    LicitacionListingEntry entry =
        row("08-03-2024", "20-08-2026", "Actuacións de mellora", new BigDecimal("3378552.09"))
            .toListingEntry();

    assertThat(entry)
        .isEqualTo(
            new LicitacionListingEntry(
                new PublicationId("822054"),
                LocalDate.of(2024, 3, 8),
                LocalDate.of(2026, 8, 20),
                "Actuacións de mellora",
                new Money(new BigDecimal("3378552.09")),
                8,
                "Formalizado"));
  }

  /** The published scale is part of the amount, and {@link Money} equality is sensitive to it. */
  @Test
  void carries_the_amount_at_the_scale_the_source_published() {
    LicitacionListingEntry entry =
        row("08-03-2024", "20-08-2026", "Obxecto", new BigDecimal("3378552.90")).toListingEntry();

    assertThat(entry.baseBudget()).isEqualTo(new Money(new BigDecimal("3378552.90")));
  }

  @Test
  void carries_no_amount_where_the_source_published_none() {
    LicitacionListingEntry entry =
        row("08-03-2024", "20-08-2026", "Obxecto", null).toListingEntry();

    assertThat(entry.baseBudget()).isNull();
  }

  @Test
  void strips_the_padding_the_source_pads_its_text_with() {
    LicitacionListingEntry entry =
        new LicitacionsRow(
                PUBLICATION_ID,
                "08-03-2024",
                "20-08-2026",
                "  Actuacións de mellora   ",
                null,
                8,
                "  Formalizado  ")
            .toListingEntry();

    assertThat(entry)
        .extracting(LicitacionListingEntry::obxecto, LicitacionListingEntry::stateLabel)
        .containsExactly("Actuacións de mellora", "Formalizado");
  }

  /**
   * The date column is nullable at the source and unreadable text is not a reason to refuse a real
   * procedure: it is surfaced as absent, and whether that hides the procedure from readers is
   * decided above the port.
   *
   * <p>{@code 31-02-2026} is in the list because of the strict resolver — the default one clamps
   * an impossible day to the month's last, which would store a date nobody published.
   */
  @Test
  void surfaces_dates_it_cannot_read_as_absent_and_still_returns_the_entry() {
    List<String> unreadable =
        Arrays.asList(null, "", "   ", "2024-03-08", "08/03/2024", "onte", "31-02-2026");

    assertThat(unreadable)
        .allSatisfy(
            published ->
                assertThat(row(published, published, "Obxecto", null).toListingEntry())
                    .extracting(
                        LicitacionListingEntry::publicationDate,
                        LicitacionListingEntry::lastModified)
                    .containsExactly(null, null));
  }

  /** The boundary the strict resolver must not refuse: a last day of February that really was. */
  @Test
  void reads_the_last_day_of_february_that_the_source_really_published() {
    LicitacionListingEntry entry =
        row("29-02-2024", "28-02-2026", "Obxecto", null).toListingEntry();

    assertThat(entry)
        .extracting(
            LicitacionListingEntry::publicationDate, LicitacionListingEntry::lastModified)
        .containsExactly(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 2, 28));
  }

  /** The two codes sharing one label are why the code is carried beside it. */
  @Test
  void carries_the_state_code_beside_the_label_it_shares_with_another_code() {
    LicitacionListingEntry entry =
        new LicitacionsRow(PUBLICATION_ID, null, null, null, null, 102, "Histórico")
            .toListingEntry();

    assertThat(entry)
        .extracting(LicitacionListingEntry::stateCode, LicitacionListingEntry::stateLabel)
        .containsExactly(102, "Histórico");
  }

  @Test
  void refuses_the_row_when_the_source_published_no_identifier() {
    LicitacionsRow row =
        new LicitacionsRow(null, "08-03-2024", "20-08-2026", "Obxecto", null, 8, "Formalizado");

    assertThatThrownBy(row::toListingEntry)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasMessageContaining("id");
  }

  /**
   * The state is the one other value a procedure cannot be stored without, and the listing is the
   * only place the source publishes its code.
   */
  @Test
  void refuses_the_row_when_the_source_published_no_state() {
    LicitacionsRow row =
        new LicitacionsRow(
            PUBLICATION_ID, "08-03-2024", "20-08-2026", "Obxecto", null, null, "Formalizado");

    assertThatThrownBy(row::toListingEntry)
        .isInstanceOf(LicitacionListingUnavailableException.class)
        .hasMessageContaining("estado");
  }

  private static LicitacionsRow row(
      String publicado, String modificado, String objeto, BigDecimal importe) {
    return new LicitacionsRow(
        PUBLICATION_ID, publicado, modificado, objeto, importe, 8, "Formalizado");
  }
}
