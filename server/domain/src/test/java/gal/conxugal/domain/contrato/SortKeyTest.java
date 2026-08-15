package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SortKeyTest {

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

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "obxecto",
        "duration",
        "sourceId",
        "PublicationDate",
        "publicationdate",
        "PUBLICATION_DATE",
        "publication_date",
        "Amount",
        "AMOUNT",
        " amount",
        "amount "
      })
  void refuses_every_other_spelling_rather_than_falling_back_to_one_of_the_two(String published) {
    assertThat(SortKey.parse(published))
        .isEmpty();
  }

  @Test
  void offers_the_two_keys_and_no_third() {
    assertThat(SortKey.values())
        .containsExactly(SortKey.PUBLICATION_DATE, SortKey.AMOUNT);
  }

  @Test
  void orders_ascending_by_the_date_column_then_the_source_identifier() {
    assertThat(SortKey.PUBLICATION_DATE.ordering(Sort.Order.Direction.ASC).getOrderBy())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(
            tuple("publication_date", Sort.Order.Direction.ASC),
            tuple("source_id", Sort.Order.Direction.ASC));
  }

  @Test
  void orders_descending_by_the_date_column_then_the_source_identifier() {
    assertThat(SortKey.PUBLICATION_DATE.ordering(Sort.Order.Direction.DESC).getOrderBy())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(
            tuple("publication_date", Sort.Order.Direction.DESC),
            tuple("source_id", Sort.Order.Direction.DESC));
  }

  @Test
  void orders_ascending_by_the_amount_column_then_the_source_identifier() {
    assertThat(SortKey.AMOUNT.ordering(Sort.Order.Direction.ASC).getOrderBy())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(
            tuple("amount", Sort.Order.Direction.ASC),
            tuple("source_id", Sort.Order.Direction.ASC));
  }

  @Test
  void orders_descending_by_the_amount_column_then_the_source_identifier() {
    assertThat(SortKey.AMOUNT.ordering(Sort.Order.Direction.DESC).getOrderBy())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(
            tuple("amount", Sort.Order.Direction.DESC),
            tuple("source_id", Sort.Order.Direction.DESC));
  }

}
