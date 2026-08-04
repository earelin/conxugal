package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkOrganoForImportTest {

  @Mock
  private OrganoRepository organoRepository;

  private MarkOrganoForImport markOrganoForImport;

  @BeforeEach
  void setUp() {
    markOrganoForImport = new MarkOrganoForImport(organoRepository);
  }

  @Test
  void marks_an_unmarked_organo() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unmarked(organoId)));

    markOrganoForImport.mark(organoId);

    verify(organoRepository).updateImportable(organoId, true);
  }

  @Test
  void marking_an_already_marked_organo_writes_nothing() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(marked(organoId)));

    markOrganoForImport.mark(organoId);

    verify(organoRepository, never()).updateImportable(any(), anyBoolean());
  }

  // An inactive Órgano is still markable: eligibility for an import run is active AND marked,
  // and it is the run that evaluates the pair, not the mark.
  @Test
  void marks_an_inactive_organo() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(organoId))
        .thenReturn(Optional.of(organo(organoId, false, false)));

    markOrganoForImport.mark(organoId);

    verify(organoRepository).updateImportable(organoId, true);
  }

  @Test
  void rejects_unknown_organo_and_writes_nothing() {
    OrganoId unknownOrganoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(unknownOrganoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> markOrganoForImport.mark(unknownOrganoId))
        .isInstanceOf(OrganoNotFoundException.class);
    verify(organoRepository, never()).updateImportable(any(), anyBoolean());
  }

  private static OrganoDeContratacion unmarked(OrganoId organoId) {
    return organo(organoId, true, false);
  }

  private static OrganoDeContratacion marked(OrganoId organoId) {
    return organo(organoId, true, true);
  }

  private static OrganoDeContratacion organo(
      OrganoId organoId, boolean active, boolean importable) {
    return new OrganoDeContratacion(
        organoId, "source-key", "Facenda", active, importable, new TermoId(UUID.randomUUID()));
  }
}
