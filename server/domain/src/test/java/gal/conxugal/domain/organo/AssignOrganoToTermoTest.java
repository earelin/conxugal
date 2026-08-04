package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
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
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    TermoId termoId = new TermoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));

    assignOrganoToTermo.assign(organoId, termoId);

    verify(organoRepository).updateTermo(organoId, termoId);
  }

  @Test
  void reassigning_replaces_the_previous_placement() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    TermoId firstTermoId = new TermoId(UUID.randomUUID());
    TermoId secondTermoId = new TermoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));
    when(termoRepository.findById(firstTermoId))
        .thenReturn(Optional.of(new Termo(firstTermoId, "Deportes", null)));
    when(termoRepository.findById(secondTermoId))
        .thenReturn(Optional.of(new Termo(secondTermoId, "Cultura", null)));

    assignOrganoToTermo.assign(organoId, firstTermoId);
    assignOrganoToTermo.assign(organoId, secondTermoId);

    // updateTermo sets the Órgano's single placement rather than adding one, so the second assign
    // supersedes the first and no third write follows. That the placement is durably singular is
    // proven against the database in JdbcOrganoRepositoryIntegrationTest.
    InOrder placements = Mockito.inOrder(organoRepository);
    placements.verify(organoRepository).updateTermo(organoId, firstTermoId);
    placements.verify(organoRepository).updateTermo(organoId, secondTermoId);
    placements.verifyNoMoreInteractions();
  }

  @Test
  void rejects_unknown_organo_and_writes_nothing() {
    OrganoId unknownOrganoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(unknownOrganoId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> assignOrganoToTermo.assign(unknownOrganoId, new TermoId(UUID.randomUUID())))
        .isInstanceOf(OrganoNotFoundException.class);
    verify(organoRepository, never()).updateTermo(any(), any());
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    TermoId unknownTermoId = new TermoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unclassified(organoId)));
    when(termoRepository.findById(unknownTermoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> assignOrganoToTermo.assign(organoId, unknownTermoId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(organoRepository, never()).updateTermo(any(), any());
  }

  private static OrganoDeContratacion unclassified(OrganoId organoId) {
    return new OrganoDeContratacion(organoId, "source-key", "Facenda", true, false, null);
  }
}
