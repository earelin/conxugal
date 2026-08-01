package gal.conxugal.domain.organo.taxonomia;

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
class DeleteTermoTest {

  @Mock
  private TermoRepository termoRepository;

  private DeleteTermo deleteTermo;

  @BeforeEach
  void setUp() {
    deleteTermo = new DeleteTermo(termoRepository);
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
  void rejects_term_that_still_has_child_terms() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.existsByParentId(termoId)).thenReturn(true);

    assertThatThrownBy(() -> deleteTermo.delete(termoId))
        .isInstanceOf(TermoHasChildrenException.class);
    verify(termoRepository, never()).deleteById(any(UUID.class));
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    UUID unknownId = UUID.randomUUID();
    when(termoRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deleteTermo.delete(unknownId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).deleteById(any(UUID.class));
  }
}
