package gal.conxugal.domain.organo;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A contracting body (Órgano de Contratación) from the Xunta de Galicia catalogue.
 * {@code sourceKey} is the stable, opaque key reconciliation matches on across imports;
 * {@code id} is a separate system-assigned identity, so a source-side rename never
 * changes it. {@code id} is {@code null} only until the database assigns it on insert.
 *
 * <p>{@code importable} is what an administrator asked for; {@code importState} is how far the
 * system got. They are independent, and the pair is why an Órgano marked minutes ago does not
 * read as loaded. The state is a value of this aggregate rather than an entity beside it — it is
 * loaded with the Órgano and reached only through it — and is {@code null} for an Órgano whose
 * import has never started, which {@link #importStatus()} reads as
 * {@link ContratosMenoresImportStatus#NEVER_STARTED} so no caller has to interpret the absence.
 */
@MappedEntity("organo_contratacion")
public record OrganoDeContratacion(
    @Id @GeneratedValue @Nullable OrganoId id,
    String sourceKey,
    String name,
    boolean active,
    boolean importable,
    @Nullable TermoId termoId,
    @Relation(value = Relation.Kind.ONE_TO_ONE, mappedBy = "organoId")
        @Nullable ContratosMenoresImportState importState) {

  public OrganoDeContratacion {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }

  /** An Órgano read without its import state, or one whose import has never started. */
  public OrganoDeContratacion(
      @Nullable OrganoId id,
      String sourceKey,
      String name,
      boolean active,
      boolean importable,
      @Nullable TermoId termoId) {
    this(id, sourceKey, name, active, importable, termoId, null);
  }

  /**
   * A newly discovered Órgano: active, unclassified and not imported until later touched.
   * Importing contratos menores is opted into deliberately, never by discovery.
   */
  public OrganoDeContratacion(String sourceKey, String name) {
    this(null, sourceKey, name, true, false, null, null);
  }

  /**
   * How far this Órgano's contratos menores history has been loaded. No state row means the
   * import never started, and answering that here is what stops each caller reading the absence
   * its own way — a half-loaded Órgano must never be mistaken for one that is up to date.
   */
  public ContratosMenoresImportStatus importStatus() {
    return importState == null
        ? ContratosMenoresImportStatus.NEVER_STARTED
        : importState.state();
  }

  /**
   * Identity, not value: an Órgano renamed, deactivated or reclassified is still the same Órgano.
   * One the database has not yet assigned an id to is equal to nothing but itself.
   */
  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof OrganoDeContratacion other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
