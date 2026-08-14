package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SortKeyTest {

  private static final List<String> NOT_A_SORT_KEY =
      Arrays.asList(
          null, "", "   ", "obxecto", "duration", "sourceId", "PublicationDate", "publicationdate",
          "PUBLICATION_DATE", "publication_date", "Amount", "AMOUNT", " amount", "amount ");

  @Test
  void parses_the_publication_date_the_contract_publishes_it_as() {
    assertThat(SortKey.parse("publicationDate"))
        .contains(SortKey.PUBLICATION_DATE);
  }

  @Test
  void parses_the_amount_the_contract_publishes_it_as() {
    assertThat(SortKey.parse("amount"))
        .contains(SortKey.AMOUNT);
  }

  @Test
  void refuses_every_other_spelling_rather_than_falling_back_to_one_of_the_two() {
    assertThat(NOT_A_SORT_KEY)
        .allSatisfy(
            published ->
                assertThat(SortKey.parse(published))
                    .isEmpty());
  }

  @Test
  void offers_the_two_keys_and_no_third() {
    assertThat(SortKey.values())
        .containsExactly(SortKey.PUBLICATION_DATE, SortKey.AMOUNT);
  }
}
