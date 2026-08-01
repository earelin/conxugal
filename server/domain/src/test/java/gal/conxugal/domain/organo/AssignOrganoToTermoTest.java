package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoNotFoundException;
import gal.conxugal.domain.organo.taxonomia.TermoRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignOrganoToTermoTest {

  @Mock
  private OrganoRepository organoRepository;

  @Mock
  private TermoRepository termoRepository;

  private AssignOrganoToTermo assignOrganoToTermo;

  @BeforeEach
  void setUp() {
    assignOrganoToTermo = new AssignOrganoToTermo(organoRepository, termoRepository);
  }

  @Test
  void files_organo_under_the_term() {
    UUID organoId = UUID.randomUUID();
    UUID termoId = UUID.randomUUID();
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));

    assignOrganoToTermo.assign(organoId, termoId);

    verify(organoRepository).updateTermo(organoId, termoId);
  }

  @Test
  void reassigning_replaces_the_previous_placement() {
    UUID organoId = UUID.randomUUID();
    UUID firstTermoId = UUID.randomUUID();
    UUID secondTermoId = UUID.randomUUID();
    when(organoRepository.findById(organoId))
        .thenReturn(Optional.of(unclassified(organoId)))
        .thenReturn(Optional.of(placedIn(organoId, firstTermoId)));
    when(termoRepository.findById(firstTermoId))
        .thenReturn(Optional.of(new Termo(firstTermoId, "Deportes", null)));
    when(termoRepository.findById(secondTermoId))
        .thenReturn(Optional.of(new Termo(secondTermoId, "Cultura", null)));

    assignOrganoToTermo.assign(organoId, firstTermoId);
    assignOrganoToTermo.assign(organoId, secondTermoId);

    // Each assign overwrites the single termo_id column rather than adding a row, so the second
    // leaves the Órgano in the second term only — there is no path by which it holds both.
    InOrder placements = Mockito.inOrder(organoRepository);
    placements.verify(organoRepository).updateTermo(organoId, firstTermoId);
    placements.verify(organoRepository).updateTermo(organoId, secondTermoId);
    verify(organoRepository, times(2)).updateTermo(any(), any());
  }

  @Test
  void rejects_unknown_organo_and_writes_nothing() {
    UUID unknownOrganoId = UUID.randomUUID();
    when(organoRepository.findById(unknownOrganoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> assignOrganoToTermo.assign(unknownOrganoId, UUID.randomUUID()))
        .isInstanceOf(OrganoNotFoundException.class);
    verify(organoRepository, never()).updateTermo(any(), any());
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    UUID organoId = UUID.randomUUID();
    UUID unknownTermoId = UUID.randomUUID();
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));
    when(termoRepository.findById(unknownTermoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> assignOrganoToTermo.assign(organoId, unknownTermoId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(organoRepository, never()).updateTermo(any(), any());
  }

  private static OrganoDeContratacion unclassified(UUID organoId) {
    return placedIn(organoId, null);
  }

  private static OrganoDeContratacion placedIn(UUID organoId, UUID termoId) {
    return new OrganoDeContratacion(organoId, "source-key", "Facenda", true, termoId);
  }
}
