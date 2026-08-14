package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Sort;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Structural, because what the port refuses is the point of it. The ordering is chosen by which
 * method is called, and a parameter naming one is how an ordering nobody reviewed — or, on a
 * native statement, a property name nobody escaped — would reach a query.
 */
class BrowseContratosMenoresTest {

  private static final List<Class<?>> ORDERING_VOCABULARY =
      List.of(Sort.class, Sort.Order.class, SortKey.class, Direction.class);

  @Test
  void declares_one_method_per_ordering_and_no_other() {
    assertThat(BrowseContratosMenores.class.getDeclaredMethods())
        .extracting(Method::getName)
        .containsExactlyInAnyOrder(
            "byPublicationDateAscending",
            "byPublicationDateDescending",
            "byAmountAscending",
            "byAmountDescending");
  }

  @Test
  void answers_every_ordering_with_one_page() {
    assertThat(BrowseContratosMenores.class.getDeclaredMethods())
        .allSatisfy(
            method ->
                assertThat(method.getReturnType())
                    .isEqualTo(Page.class));
  }

  @Test
  void takes_no_sort_key_or_direction_through_which_an_ordering_could_be_named() {
    assertThat(BrowseContratosMenores.class.getDeclaredMethods())
        .allSatisfy(
            method ->
                assertThat(Arrays.asList(method.getParameterTypes()))
                    .doesNotContainAnyElementsOf(ORDERING_VOCABULARY));
  }
}
