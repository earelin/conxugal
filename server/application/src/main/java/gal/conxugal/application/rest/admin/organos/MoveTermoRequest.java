package gal.conxugal.application.rest.admin.organos;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * The term to move a term under. A null {@code parentId} moves it to the root, which is the
 * whole reason the field is nullable rather than required: a move out of a subtree has no
 * other way to say where it lands.
 */
@Serdeable
public record MoveTermoRequest(@Nullable UUID parentId) {
}
