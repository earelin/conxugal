package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MoveTermoTest {

  @Mock
  private TermoRepository termoRepository;

  private MoveTermo moveTermo;

  @BeforeEach
  void setUp() {
    moveTermo = new MoveTermo(termoRepository);
  }

  @Test
  void moves_term_under_another_parent() {
    UUID termoId = UUID.randomUUID();
    UUID newParentId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", UUID.randomUUID())));
    when(termoRepository.findById(newParentId))
        .thenReturn(Optional.of(new Termo(newParentId, "Cultura", null)));
    when(termoRepository.findByParentId(newParentId)).thenReturn(List.of());

    moveTermo.move(termoId, newParentId);

    verify(termoRepository).updateParentId(termoId, newParentId);
  }

  @Test
  void moves_term_to_the_root() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", UUID.randomUUID())));
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "Cultura", null)));

    moveTermo.move(termoId, null);

    verify(termoRepository).updateParentId(termoId, null);
  }

  @Test
  void rejects_move_onto_itself() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));

    assertThatThrownBy(() -> moveTermo.move(termoId, termoId))
        .isInstanceOf(TermoCycleException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_move_onto_its_own_grandchild() {
    UUID termoId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();
    UUID grandchildId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.findById(childId))
        .thenReturn(Optional.of(new Termo(childId, "Fútbol", termoId)));
    when(termoRepository.findById(grandchildId))
        .thenReturn(Optional.of(new Termo(grandchildId, "Liga", childId)));

    assertThatThrownBy(() -> moveTermo.move(termoId, grandchildId))
        .isInstanceOf(TermoCycleException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_move_when_the_stored_ancestry_already_contains_cycle() {
    UUID termoId = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    // Two concurrent moves can leave these two pointing at each other, since the feature
    // accepts the check-then-write window. Walking that chain must end, not circle it.
    when(termoRepository.findById(firstId))
        .thenReturn(Optional.of(new Termo(firstId, "Cultura", secondId)));
    when(termoRepository.findById(secondId))
        .thenReturn(Optional.of(new Termo(secondId, "Música", firstId)));

    assertThatThrownBy(() -> moveTermo.move(termoId, firstId))
        .isInstanceOf(TermoCycleException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    UUID unknownId = UUID.randomUUID();
    when(termoRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> moveTermo.move(unknownId, UUID.randomUUID()))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_unknown_target_parent_and_writes_nothing() {
    UUID termoId = UUID.randomUUID();
    UUID unknownParentId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", null)));
    when(termoRepository.findById(unknownParentId)).thenReturn(Optional.empty());

    // Walking the ancestry of a parent that does not exist ends immediately and finds no
    // cycle, so without this check the move would be accepted against a missing row and
    // surface as a 500 rather than the 404 the feature promises.
    assertThatThrownBy(() -> moveTermo.move(termoId, unknownParentId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_name_already_used_at_the_destination() {
    UUID termoId = UUID.randomUUID();
    UUID newParentId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", UUID.randomUUID())));
    when(termoRepository.findById(newParentId))
        .thenReturn(Optional.of(new Termo(newParentId, "Cultura", null)));
    when(termoRepository.findByParentId(newParentId))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "fútbol", newParentId)));

    assertThatThrownBy(() -> moveTermo.move(termoId, newParentId))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }

  @Test
  void rejects_move_to_the_root_colliding_with_existing_root() {
    UUID termoId = UUID.randomUUID();
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", UUID.randomUUID())));
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "Deportes", null)));

    assertThatThrownBy(() -> moveTermo.move(termoId, null))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).updateParentId(any(), any());
  }
}
