package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Sort;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Structural, because what the port refuses is the point of it. The ordering is chosen by which
 * method is called, so a fifth method — or a parameter naming an ordering — is how an ordering
 * nobody reviewed, or a property name nobody escaped, would reach a native statement.
 */
class VisibleContratoMenorRepositoryTest {

  private static final String PAGE_OF_VISIBLE_CONTRACTS =
      "%s<%s>".formatted(Page.class.getName(), VisibleContratoMenor.class.getName());

  private static final List<Class<?>> ORDERING_VOCABULARY =
      List.of(Sort.class, Sort.Order.class, SortKey.class, SortDirection.class);

  @Test
  void declares_one_method_per_ordering_and_no_other() {
    assertThat(VisibleContratoMenorRepository.class.getDeclaredMethods())
        .extracting(Method::getName, method -> method.getGenericReturnType().getTypeName())
        .containsExactlyInAnyOrder(
            tuple("byPublicationDateAscending", PAGE_OF_VISIBLE_CONTRACTS),
            tuple("byPublicationDateDescending", PAGE_OF_VISIBLE_CONTRACTS),
            tuple("byAmountAscending", PAGE_OF_VISIBLE_CONTRACTS),
            tuple("byAmountDescending", PAGE_OF_VISIBLE_CONTRACTS));
  }

  @Test
  void takes_no_sort_key_or_direction_through_which_an_ordering_could_be_named() {
    assertThat(VisibleContratoMenorRepository.class.getDeclaredMethods())
        .allSatisfy(
            method ->
                assertThat(Arrays.asList(method.getParameterTypes()))
                    .doesNotContainAnyElementsOf(ORDERING_VOCABULARY));
  }
}
