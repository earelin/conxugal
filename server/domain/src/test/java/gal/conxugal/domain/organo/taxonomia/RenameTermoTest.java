package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
class RenameTermoTest {

  @Mock
  private TermoRepository termoRepository;

  private RenameTermo renameTermo;

  @BeforeEach
  void setUp() {
    renameTermo = new RenameTermo(termoRepository);
  }

  @Test
  void renames_term_leaving_its_place_in_the_tree_untouched() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    TermoId termoId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", parentId)));
    when(termoRepository.findByParentId(parentId))
        .thenReturn(List.of(new Termo(termoId, "Fútbol", parentId)));

    Termo renamed = renameTermo.rename(termoId, "Fútbol Sala");

    verify(termoRepository).updateName(termoId, "Fútbol Sala");
    assertThat(renamed).isEqualTo(new Termo(termoId, "Fútbol Sala", parentId));
  }

  @Test
  void rejects_unknown_term_and_writes_nothing() {
    TermoId unknownId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> renameTermo.rename(unknownId, "Fútbol"))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).updateName(any(TermoId.class), anyString());
  }

  @Test
  void rejects_name_already_used_by_sibling() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    TermoId termoId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Fútbol", parentId)));
    when(termoRepository.findByParentId(parentId))
        .thenReturn(
            List.of(
                new Termo(termoId, "Fútbol", parentId),
                new Termo(new TermoId(UUID.randomUUID()), "Baloncesto", parentId)));

    assertThatThrownBy(() -> renameTermo.rename(termoId, "baloncesto"))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).updateName(any(TermoId.class), anyString());
  }

  @Test
  void accepts_renaming_term_to_its_own_current_name() {
    TermoId termoId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(termoId, "Deportes", null)));

    renameTermo.rename(termoId, "Deportes");

    verify(termoRepository).updateName(termoId, "Deportes");
  }

  @Test
  void stores_the_name_stripped() {
    TermoId termoId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(termoId, "Deportes", null)));

    Termo renamed = renameTermo.rename(termoId, "  Cultura  ");

    verify(termoRepository).updateName(termoId, "Cultura");
    assertThat(renamed.name()).isEqualTo("Cultura");
  }
}
