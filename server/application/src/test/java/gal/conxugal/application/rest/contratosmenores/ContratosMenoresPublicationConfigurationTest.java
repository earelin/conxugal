package gal.conxugal.application.rest.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContratosMenoresPublicationConfigurationTest {

  @Test
  void composes_the_publication_address_from_the_source_identifier() {
    ContratosMenoresPublicationConfiguration publication =
        new ContratosMenoresPublicationConfiguration("https://www.contratosdegalicia.gal");

    assertThat(publication.urlOf(1234567))
        .isEqualTo("https://www.contratosdegalicia.gal/licitacion?N=1234567");
  }

  // A base configured with the trailing slash a reader would naturally type must not produce a
  // link carrying two, since nothing downstream normalises one.
  @Test
  void drops_trailing_slashes_from_the_configured_base() {
    ContratosMenoresPublicationConfiguration publication =
        new ContratosMenoresPublicationConfiguration("https://www.contratosdegalicia.gal//");

    assertThat(publication.urlOf(42))
        .isEqualTo("https://www.contratosdegalicia.gal/licitacion?N=42");
  }

  @Test
  void refuses_an_unusable_base_nobody_could_follow() {
    assertThatThrownBy(() -> new ContratosMenoresPublicationConfiguration("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("base-url must be set");
  }

  @Test
  void refuses_the_base_that_is_nothing_but_slashes() {
    assertThatThrownBy(() -> new ContratosMenoresPublicationConfiguration("///"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("more than slashes");
  }
}
