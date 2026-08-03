package gal.conxugal.application.rest.organos;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One term of the taxonomía, carrying only the edge to its parent — never its children or
 * the Órganos filed under it. A null {@code parentId} is a root, and is sent as an explicit
 * null rather than dropped under the serializer's default {@code NON_EMPTY} inclusion: a
 * caller groups by it to find the roots.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TermoResponse(UUID id, String name, @Nullable UUID parentId) {

  public static TermoResponse of(Termo termo) {
    return new TermoResponse(
        requireNonNull(termo.id(), "a stored term must carry an id").value(), termo.name(),
        idOf(termo.parentId()));
  }

  private static @Nullable UUID idOf(@Nullable TermoId parentId) {
    return parentId == null ? null : parentId.value();
  }
}
