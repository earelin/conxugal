package gal.conxugal.domain.organo.taxonomia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListTermosTest {

  @Mock
  private TermoRepository termoRepository;

  private ListTermos listTermos;

  @BeforeEach
  void setUp() {
    listTermos = new ListTermos(termoRepository);
  }

  @Test
  void returns_every_term_with_its_parent() {
    UUID rootId = UUID.randomUUID();
    Termo root = new Termo(rootId, "Deportes", null);
    Termo child = new Termo(UUID.randomUUID(), "Fútbol", rootId);
    when(termoRepository.findAllOrderByName()).thenReturn(List.of(root, child));

    List<Termo> result = listTermos.list();

    assertThat(result).containsExactly(root, child);
  }

  @Test
  void returns_empty_list_when_no_term_is_stored() {
    when(termoRepository.findAllOrderByName()).thenReturn(List.of());

    List<Termo> result = listTermos.list();

    assertThat(result).isEmpty();
  }
}
