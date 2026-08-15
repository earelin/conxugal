package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Structural, because the port's shape is what the rest of the read rests on. One read, scoped by
 * an Órgano and a year, answering a page of the whole year's count — everything about the ordering
 * travels on the {@code Pageable} the caller builds.
 */
class VisibleContratoMenorRepositoryTest {

  private static final String PAGE_OF_VISIBLE_CONTRACTS =
      "%s<%s>".formatted(Page.class.getName(), VisibleContratoMenor.class.getName());

  @Test
  void declares_one_scoped_read_answering_one_page() {
    assertThat(VisibleContratoMenorRepository.class.getDeclaredMethods())
        .extracting(Method::getName, method -> method.getGenericReturnType().getTypeName())
        .containsExactly(tuple("page", PAGE_OF_VISIBLE_CONTRACTS));
  }

  @Test
  void takes_the_organo_the_year_and_the_page_request_and_nothing_else() {
    assertThat(VisibleContratoMenorRepository.class.getDeclaredMethods())
        .singleElement()
        .extracting(Method::getParameterTypes)
        .isEqualTo(new Class<?>[] {OrganoId.class, YearSelection.class, Pageable.class});
  }
}
