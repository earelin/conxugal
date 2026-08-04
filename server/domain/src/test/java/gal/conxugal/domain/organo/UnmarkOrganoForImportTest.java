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
class UnmarkOrganoForImportTest {

  @Mock
  private OrganoRepository organoRepository;

  private UnmarkOrganoForImport unmarkOrganoForImport;

  @BeforeEach
  void setUp() {
    unmarkOrganoForImport = new UnmarkOrganoForImport(organoRepository);
  }

  @Test
  void unmarks_marked_organo() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(marked(organoId)));

    unmarkOrganoForImport.unmark(organoId);

    verify(organoRepository).updateImportable(organoId, false);
  }

  @Test
  void unmarking_an_unmarked_organo_writes_nothing() {
    OrganoId organoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(organoId)).thenReturn(Optional.of(unmarked(organoId)));

    unmarkOrganoForImport.unmark(organoId);

    verify(organoRepository, never()).updateImportable(any(), anyBoolean());
  }

  @Test
  void rejects_unknown_organo_and_writes_nothing() {
    OrganoId unknownOrganoId = new OrganoId(UUID.randomUUID());
    when(organoRepository.findById(unknownOrganoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> unmarkOrganoForImport.unmark(unknownOrganoId))
        .isInstanceOf(OrganoNotFoundException.class);
    verify(organoRepository, never()).updateImportable(any(), anyBoolean());
  }

  private static OrganoDeContratacion unmarked(OrganoId organoId) {
    return organo(organoId, false);
  }

  private static OrganoDeContratacion marked(OrganoId organoId) {
    return organo(organoId, true);
  }

  private static OrganoDeContratacion organo(OrganoId organoId, boolean importable) {
    return new OrganoDeContratacion(
        organoId, "source-key", "Facenda", true, importable, new TermoId(UUID.randomUUID()));
  }
}
