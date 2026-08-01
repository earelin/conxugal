package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClearOrganoTermoTest {

  @Mock
  private OrganoRepository organoRepository;

  private ClearOrganoTermo clearOrganoTermo;

  @BeforeEach
  void setUp() {
    clearOrganoTermo = new ClearOrganoTermo(organoRepository);
  }

  @Test
  void returns_placed_organo_to_the_unclassified_set() {
    UUID organoId = UUID.randomUUID();
    when(organoRepository.findById(organoId))
        .thenReturn(Optional.of(placedIn(organoId, UUID.randomUUID())));

    clearOrganoTermo.clear(organoId);

    verify(organoRepository).updateTermo(organoId, null);
  }

  @Test
  void clearing_an_already_unclassified_organo_writes_nothing() {
    UUID organoId = UUID.randomUUID();
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));

    clearOrganoTermo.clear(organoId);

    verify(organoRepository, never()).updateTermo(any(), any());
  }

  @Test
  void rejects_unknown_organo_and_writes_nothing() {
    UUID unknownOrganoId = UUID.randomUUID();
    when(organoRepository.findById(unknownOrganoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> clearOrganoTermo.clear(unknownOrganoId))
        .isInstanceOf(OrganoNotFoundException.class);
    verify(organoRepository, never()).updateTermo(any(), any());
  }

  private static OrganoDeContratacion unclassified(UUID organoId) {
    return placedIn(organoId, null);
  }

  private static OrganoDeContratacion placedIn(UUID organoId, UUID termoId) {
    return new OrganoDeContratacion(organoId, "source-key", "Facenda", true, termoId);
  }
}
