package gal.conxugal.domain.organo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

final class FakeOrganoRepository implements OrganoRepository {

  private final List<OrganoDeContratacion> stored = new ArrayList<>();

  @Override
  public List<OrganoDeContratacion> findAll() {
    return List.copyOf(stored);
  }

  @Override
  public List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys) {
    return stored.stream().filter(organo -> sourceKeys.contains(organo.sourceKey())).toList();
  }

  @Override
  public OrganoDeContratacion insert(OrganoDeContratacion organo) {
    OrganoDeContratacion withId =
        new OrganoDeContratacion(
            UUID.randomUUID(), organo.sourceKey(), organo.name(), organo.active());
    stored.add(withId);
    return withId;
  }

  @Override
  public void update(UUID id, String name, boolean active) {
    replace(id, organo -> new OrganoDeContratacion(id, organo.sourceKey(), name, active));
  }

  @Override
  public void updateActive(UUID id, boolean active) {
    replace(
        id, organo -> new OrganoDeContratacion(id, organo.sourceKey(), organo.name(), active));
  }

  OrganoDeContratacion seed(String sourceKey, String name, boolean active) {
    OrganoDeContratacion organo =
        new OrganoDeContratacion(UUID.randomUUID(), sourceKey, name, active);
    stored.add(organo);
    return organo;
  }

  private void replace(UUID id, Function<OrganoDeContratacion, OrganoDeContratacion> mapper) {
    for (int i = 0; i < stored.size(); i++) {
      if (id.equals(stored.get(i).id())) {
        stored.set(i, mapper.apply(stored.get(i)));
        return;
      }
    }
    throw new IllegalStateException("No stored Órgano with id " + id);
  }
}
