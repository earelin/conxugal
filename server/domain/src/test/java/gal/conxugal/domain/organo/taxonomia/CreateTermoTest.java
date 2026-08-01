package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThat;
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
class CreateTermoTest {

  @Mock
  private TermoRepository termoRepository;

  private CreateTermo createTermo;

  @BeforeEach
  void setUp() {
    createTermo = new CreateTermo(termoRepository);
  }

  @Test
  void creates_term_at_the_root() {
    when(termoRepository.findByParentId(null)).thenReturn(List.of());
    Termo stored = new Termo(UUID.randomUUID(), "Deportes", null);
    when(termoRepository.insert(new Termo("Deportes", null))).thenReturn(stored);

    Termo created = createTermo.create("Deportes", null);

    assertThat(created).isEqualTo(stored);
  }

  @Test
  void creates_term_under_an_existing_parent() {
    UUID parentId = UUID.randomUUID();
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Deportes", null)));
    when(termoRepository.findByParentId(parentId)).thenReturn(List.of());
    Termo stored = new Termo(UUID.randomUUID(), "Fútbol", parentId);
    when(termoRepository.insert(new Termo("Fútbol", parentId))).thenReturn(stored);

    Termo created = createTermo.create("Fútbol", parentId);

    assertThat(created).isEqualTo(stored);
  }

  @Test
  void rejects_unknown_parent_and_writes_nothing() {
    UUID unknownParentId = UUID.randomUUID();
    when(termoRepository.findById(unknownParentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createTermo.create("Fútbol", unknownParentId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_name_already_used_by_sibling() {
    UUID parentId = UUID.randomUUID();
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Deportes", null)));
    when(termoRepository.findByParentId(parentId))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "Fútbol", parentId)));

    assertThatThrownBy(() -> createTermo.create("Fútbol", parentId))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_second_root_with_the_same_name() {
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "Deportes", null)));

    assertThatThrownBy(() -> createTermo.create("Deportes", null))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_name_differing_only_in_case() {
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(UUID.randomUUID(), "Deportes", null)));

    assertThatThrownBy(() -> createTermo.create("DEPORTES", null))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void accepts_the_same_name_under_another_parent() {
    UUID parentId = UUID.randomUUID();
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Cultura", null)));
    when(termoRepository.findByParentId(parentId)).thenReturn(List.of());
    Termo stored = new Termo(UUID.randomUUID(), "Fútbol", parentId);
    when(termoRepository.insert(new Termo("Fútbol", parentId))).thenReturn(stored);

    Termo created = createTermo.create("Fútbol", parentId);

    assertThat(created).isEqualTo(stored);
  }

  @Test
  void stores_the_name_stripped() {
    when(termoRepository.findByParentId(null)).thenReturn(List.of());
    Termo stored = new Termo(UUID.randomUUID(), "Deportes", null);
    when(termoRepository.insert(new Termo("Deportes", null))).thenReturn(stored);

    Termo created = createTermo.create("  Deportes  ", null);

    assertThat(created.name()).isEqualTo("Deportes");
  }
}
