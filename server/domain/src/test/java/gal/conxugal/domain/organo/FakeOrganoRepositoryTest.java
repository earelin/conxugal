package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeOrganoRepositoryTest {

  private final FakeOrganoRepository repository = new FakeOrganoRepository();

  @Test
  void update_preserves_existing_placement() {
    UUID termoId = UUID.randomUUID();
    OrganoDeContratacion organo = repository.seed("consorcio-x", "Consorcio X", true, termoId);

    repository.update(organo.id(), "Consorcio X Renamed", true);

    assertThat(repository.findById(organo.id()).orElseThrow().termoId()).isEqualTo(termoId);
  }

  @Test
  void updateActive_preserves_existing_placement() {
    UUID termoId = UUID.randomUUID();
    OrganoDeContratacion organo = repository.seed("consorcio-x", "Consorcio X", true, termoId);

    repository.updateActive(organo.id(), false);

    assertThat(repository.findById(organo.id()).orElseThrow().termoId()).isEqualTo(termoId);
  }

  @Test
  void updateTermo_replaces_rather_than_adds_second_placement() {
    OrganoDeContratacion organo = repository.seed("consorcio-x", "Consorcio X", true);
    UUID firstTermo = UUID.randomUUID();
    UUID secondTermo = UUID.randomUUID();

    repository.updateTermo(organo.id(), firstTermo);
    repository.updateTermo(organo.id(), secondTermo);

    assertThat(repository.findById(organo.id()).orElseThrow().termoId()).isEqualTo(secondTermo);
  }

  @Test
  void updateTermo_null_clears_the_placement() {
    UUID termoId = UUID.randomUUID();
    OrganoDeContratacion organo = repository.seed("consorcio-x", "Consorcio X", true, termoId);

    repository.updateTermo(organo.id(), null);

    assertThat(repository.findById(organo.id()).orElseThrow().termoId()).isNull();
  }

  @Test
  void clearPlacementsByTermo_clears_only_rows_placed_in_that_term() {
    UUID termoId = UUID.randomUUID();
    UUID otherTermoId = UUID.randomUUID();
    OrganoDeContratacion placed = repository.seed("consorcio-x", "Consorcio X", true, termoId);
    OrganoDeContratacion placedElsewhere =
        repository.seed("axencia-y", "Axencia Y", true, otherTermoId);

    repository.clearPlacementsByTermo(termoId);

    assertThat(repository.findById(placed.id()).orElseThrow().termoId()).isNull();
    assertThat(repository.findById(placedElsewhere.id()).orElseThrow().termoId())
        .isEqualTo(otherTermoId);
  }

  @Test
  void findById_returns_empty_for_an_unknown_id() {
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
  }
}
