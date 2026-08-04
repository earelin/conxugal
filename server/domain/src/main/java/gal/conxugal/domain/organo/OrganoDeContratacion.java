package gal.conxugal.domain.organo;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A contracting body (Órgano de Contratación) from the Xunta de Galicia catalogue.
 * {@code sourceKey} is the stable, opaque key reconciliation matches on across imports;
 * {@code id} is a separate system-assigned identity, so a source-side rename never
 * changes it. {@code id} is {@code null} only until the database assigns it on insert.
 */
@MappedEntity("organo_contratacion")
public record OrganoDeContratacion(
    @Id @GeneratedValue @Nullable OrganoId id,
    String sourceKey,
    String name,
    boolean active,
    boolean importable,
    @Nullable TermoId termoId) {

  public OrganoDeContratacion {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }

  /**
   * A newly discovered Órgano: active, unclassified and not imported until later touched.
   * Importing contratos menores is opted into deliberately, never by discovery.
   */
  public OrganoDeContratacion(String sourceKey, String name) {
    this(null, sourceKey, name, true, false, null);
  }
}
