package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewOrganoTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());

  @Mock
  private OrganoRepository organoRepository;

  private ViewOrgano viewOrgano;

  @BeforeEach
  void setUp() {
    viewOrgano = new ViewOrgano(organoRepository);
  }

  @Test
  void answers_the_organo_stored_under_that_id() {
    OrganoDeContratacion sanidade =
        new OrganoDeContratacion(ORGANO_ID, "sanidade", "Sanidade", true, false, null);
    when(organoRepository.findById(ORGANO_ID)).thenReturn(Optional.of(sanidade));

    assertThat(viewOrgano.view(ORGANO_ID)).isEqualTo(sanidade);
  }

  // Inactive and unmarked is still an Órgano that exists. This read narrows nothing: which
  // contracts are visible is a property of the contracts, so nothing about the Órgano's own
  // state is grounds for refusing to answer for it.
  @Test
  void answers_an_inactive_organo_no_import_ever_touched() {
    OrganoDeContratacion retired =
        new OrganoDeContratacion(ORGANO_ID, "retirado", "Órgano retirado", false, false, null);
    when(organoRepository.findById(ORGANO_ID)).thenReturn(Optional.of(retired));

    assertThat(viewOrgano.view(ORGANO_ID)).isEqualTo(retired);
  }

  // Refused rather than answered with nothing: a caller that named an id it made up has a wrong
  // identifier, not an empty result. The carried id is what the problem body reports back.
  @Test
  void refuses_an_id_no_organo_is_stored_under() {
    when(organoRepository.findById(ORGANO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> viewOrgano.view(ORGANO_ID))
        .isInstanceOfSatisfying(
            OrganoNotFoundException.class,
            refusal -> assertThat(refusal.getOrganoId()).isEqualTo(ORGANO_ID));
  }
}
