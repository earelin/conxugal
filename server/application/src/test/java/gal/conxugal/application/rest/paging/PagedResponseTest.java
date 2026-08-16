package gal.conxugal.application.rest.paging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class PagedResponseTest {

  // The whole point of the envelope: the store counts from zero and everything above the
  // controller counts from one, so the third page of a selection is page 3 on the wire, in the
  // URL and in the control that renders it.
  @Test
  void answers_the_page_number_one_greater_than_the_one_the_store_holds() {
    Page<String> page = Page.of(List.of("a", "b"), Pageable.from(2, 50), 1832L);

    PagedResponse<String> response = PagedResponse.of(page, value -> value);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.page()).isEqualTo(3);
      softly.assertThat(response.size()).isEqualTo(50);
      softly.assertThat(response.totalItems()).isEqualTo(1832L);
      softly.assertThat(response.items()).containsExactly("a", "b");
    });
  }

  @Test
  void answers_the_first_page_as_one_rather_than_zero() {
    Page<String> page = Page.of(List.of("a"), Pageable.from(0, 50), 1L);

    assertThat(PagedResponse.of(page, value -> value).page()).isEqualTo(1);
  }

  // Taken from the page rather than divided again here: two expressions of one number is what
  // would let the span disagree with the count, at the last page, where nobody looks.
  @Test
  void takes_the_page_span_from_the_page_rather_than_dividing_the_count_again() {
    Page<String> page = Page.of(List.of("a"), Pageable.from(0, 50), 1832L);

    assertThat(PagedResponse.of(page, value -> value).totalPages()).isEqualTo(37);
  }

  // A page beyond the last is answered, not refused, and what it has to carry is the selection's
  // own totals — those are what a client clamps to. A response echoing the count of the rows it
  // returned would say the selection was empty.
  @Test
  void carries_the_whole_selections_totals_on_the_page_beyond_the_last() {
    Page<String> page = Page.of(List.of(), Pageable.from(99, 50), 1832L);

    PagedResponse<String> response = PagedResponse.of(page, value -> value);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.items()).isEmpty();
      softly.assertThat(response.page()).isEqualTo(100);
      softly.assertThat(response.totalItems()).isEqualTo(1832L);
      softly.assertThat(response.totalPages()).isEqualTo(37);
    });
  }

  @Test
  void answers_no_pages_at_all_for_an_empty_selection() {
    Page<String> page = Page.of(List.of(), Pageable.from(0, 50), 0L);

    PagedResponse<String> response = PagedResponse.of(page, value -> value);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.totalItems()).isZero();
      softly.assertThat(response.totalPages()).isZero();
    });
  }

  // The empty list surviving the mapping is only half of it: the serializer's default inclusion
  // drops an empty collection, which would take `items` out of exactly the response that needs it
  // most — the contract declares it required, and a client reading a page past the last one would
  // otherwise have to tell an empty page from a field the server forgot.
  @Test
  void serialises_an_empty_page_with_an_explicit_empty_items() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    Page<String> page = Page.of(List.of(), Pageable.from(99, 50), 1832L);
    PagedResponse<String> response = PagedResponse.of(page, value -> value);

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap)).containsEntry("items", List.of());
  }

  @Test
  void puts_every_entry_through_the_mapping_it_was_given() {
    Page<String> page = Page.of(List.of("a", "b"), Pageable.from(0, 50), 2L);

    assertThat(PagedResponse.of(page, String::toUpperCase).items()).containsExactly("A", "B");
  }
}
