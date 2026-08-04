package gal.conxugal.domain.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListOrganosTest {

  @Mock
  private OrganoRepository organoRepository;

  private ListOrganos listOrganos;

  @BeforeEach
  void setUp() {
    listOrganos = new ListOrganos(organoRepository);
  }

  @Test
  void returns_every_organo_classified_and_unclassified() {
    OrganoDeContratacion classified =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "facenda", "Facenda", true, false,
            new TermoId(UUID.randomUUID()));
    OrganoDeContratacion unclassified =
        new OrganoDeContratacion(
            new OrganoId(UUID.randomUUID()), "sanidade", "Sanidade", false, false, null);
    when(organoRepository.findAllOrderByName()).thenReturn(List.of(classified, unclassified));

    List<OrganoDeContratacion> result = listOrganos.list();

    assertThat(result).containsExactly(classified, unclassified);
  }
}
