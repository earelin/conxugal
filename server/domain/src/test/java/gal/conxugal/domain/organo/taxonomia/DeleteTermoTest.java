package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.OrganoRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteTermoTest {

  @Mock
  private TermoRepository termoRepository;

  @Mock
  private OrganoRepository organoRepository;

  private DeleteTermo deleteTermo;

  @BeforeEach
  void setUp() {
    deleteTermo = new DeleteTermo(termoRepository, organoRepository);
  }

  @Test
  void deletes_term_without_children() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.existsByParentId(termoId)).thenReturn(false);

    deleteTermo.delete(termoId);

    verify(termoRepository).deleteById(termoId);
  }

  @Test
  void returns_placed_organos_to_unclassified() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.existsByParentId(termoId)).thenReturn(false);

    deleteTermo.delete(termoId);

    // The port carries no delete at all, so no Órgano can be removed on this path — only
    // the placement pointing at the vanishing term is cleared.
    verify(organoRepository).clearPlacementsByTermo(termoId);
  }

  @Test
  void rejects_term_that_still_has_child_terms() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.existsByParentId(termoId)).thenReturn(true);

    assertThatThrownBy(() -> deleteTermo.delete(termoId))
        .isInstanceOf(TermoHasChildrenException.class);
    verify(termoRepository, never()).deleteById(any(UUID.class));
    verifyNoInteractions(organoRepository);
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    UUID unknownId = UUID.randomUUID();
    when(termoRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deleteTermo.delete(unknownId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).deleteById(any(UUID.class));
    verifyNoInteractions(organoRepository);
  }
}
