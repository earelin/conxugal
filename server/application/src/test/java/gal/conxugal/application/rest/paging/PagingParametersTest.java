package gal.conxugal.application.rest.paging;

import static gal.conxugal.application.rest.paging.PagingParameters.pageableOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.data.model.Pageable;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zalando.problem.ThrowableProblem;

class PagingParametersTest {

  @Test
  void neither_parameter_sent_reads_the_first_page_of_fifty() {
    Pageable pageable = pageableOf(null, null);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(pageable.getNumber()).isZero();
      softly.assertThat(pageable.getSize()).isEqualTo(50);
    });
  }

  // The change of base, which is the whole reason this is one implementation rather than three:
  // the wire counts from one and the store counts from zero, and no other layer has to know it.
  @Test
  void the_contracts_page_becomes_the_stores_page_one_lower() {
    assertThat(pageableOf("3", "25").getNumber()).isEqualTo(2);
  }

  @Test
  void the_first_page_the_contract_offers_becomes_the_stores_zeroth() {
    assertThat(pageableOf("1", "50").getNumber()).isZero();
  }

  // The ordering is the use case's to build, from the closed set its operation offers. A pageable
  // arriving with one would be a second source for a clause a native statement takes exactly once.
  @Test
  void pageable_carries_no_ordering_of_its_own() {
    assertThat(pageableOf("2", "10").getSort().isSorted()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "page below the first one, 0, 50, page is 1-based",
      "page negative, -1, 50, page is 1-based",
      "size below one, 1, 0, size must be between 1 and 100",
      "size above the hundred allowed, 1, 101, size must be between 1 and 100",
  })
  void value_outside_its_range_is_refused_rather_than_clamped(
      String reason, String page, String size, String expected) {
    assertThatThrownBy(() -> pageableOf(page, size))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining(expected);
  }

  @Test
  void widest_size_the_contract_offers_is_accepted() {
    assertThat(pageableOf("1", "100").getSize()).isEqualTo(100);
  }

  // Empty is not absent, and answering it with the default is what the framework's own binder did.
  @ParameterizedTest(name = "{0}")
  @CsvSource(value = {
      "page sent empty|''|50",
      "size sent empty|1|''",
  }, delimiter = '|')
  void value_sent_empty_is_refused_rather_than_defaulted(String reason, String page, String size) {
    assertThatThrownBy(() -> pageableOf(page, size))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("must be a whole number");
  }
}
