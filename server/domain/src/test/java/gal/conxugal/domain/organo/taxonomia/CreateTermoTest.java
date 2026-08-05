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

  /**
   * Stands in for the database assigning the id, echoing back what was handed to it — the id
   * apart, the returned term is the inserted one, so asserting on it asserts on the insert.
   */
  private void insertAssigningAnId() {
    when(termoRepository.insert(any(Termo.class)))
        .thenAnswer(
            invocation -> {
              Termo inserted = invocation.getArgument(0);
              return new Termo(
                  new TermoId(UUID.randomUUID()), inserted.name(), inserted.parentId());
            });
  }

  @Test
  void creates_term_at_the_root() {
    when(termoRepository.findByParentId(null)).thenReturn(List.of());
    insertAssigningAnId();

    Termo created = createTermo.create("Deportes", null);

    assertThat(created)
        .extracting(Termo::name, Termo::parentId)
        .containsExactly("Deportes", null);
  }

  @Test
  void creates_term_under_an_existing_parent() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Deportes", null)));
    when(termoRepository.findByParentId(parentId)).thenReturn(List.of());
    insertAssigningAnId();

    Termo created = createTermo.create("Fútbol", parentId);

    assertThat(created)
        .extracting(Termo::name, Termo::parentId)
        .containsExactly("Fútbol", parentId);
  }

  @Test
  void rejects_unknown_parent_and_writes_nothing() {
    TermoId unknownParentId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(unknownParentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createTermo.create("Fútbol", unknownParentId))
        .isInstanceOf(TermoNotFoundException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_name_already_used_by_sibling() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Deportes", null)));
    when(termoRepository.findByParentId(parentId))
        .thenReturn(List.of(new Termo(new TermoId(UUID.randomUUID()), "Fútbol", parentId)));

    assertThatThrownBy(() -> createTermo.create("Fútbol", parentId))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_second_root_with_the_same_name() {
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(new TermoId(UUID.randomUUID()), "Deportes", null)));

    assertThatThrownBy(() -> createTermo.create("Deportes", null))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void rejects_name_differing_only_in_case() {
    when(termoRepository.findByParentId(null))
        .thenReturn(List.of(new Termo(new TermoId(UUID.randomUUID()), "Deportes", null)));

    assertThatThrownBy(() -> createTermo.create("DEPORTES", null))
        .isInstanceOf(DuplicateSiblingNameException.class);
    verify(termoRepository, never()).insert(any(Termo.class));
  }

  @Test
  void accepts_the_same_name_under_another_parent() {
    TermoId parentId = new TermoId(UUID.randomUUID());
    when(termoRepository.findById(parentId))
        .thenReturn(Optional.of(new Termo(parentId, "Cultura", null)));
    when(termoRepository.findByParentId(parentId)).thenReturn(List.of());
    insertAssigningAnId();

    Termo created = createTermo.create("Fútbol", parentId);

    assertThat(created)
        .extracting(Termo::name, Termo::parentId)
        .containsExactly("Fútbol", parentId);
  }

  @Test
  void stores_the_name_stripped() {
    when(termoRepository.findByParentId(null)).thenReturn(List.of());
    insertAssigningAnId();

    Termo created = createTermo.create("  Deportes  ", null);

    assertThat(created.name()).isEqualTo("Deportes");
  }
}
