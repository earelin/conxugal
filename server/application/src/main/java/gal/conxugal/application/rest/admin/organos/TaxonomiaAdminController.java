package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.application.rest.organos.TermoResponse;
import gal.conxugal.domain.organo.taxonomia.CreateTermo;
import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.MoveTermo;
import gal.conxugal.domain.organo.taxonomia.RenameTermo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Building and reshaping the taxonomía. Every rule — the cycle guard, the child-term
 * refusal, the sibling-name rule — belongs to the use cases; this controller only moves
 * values across the boundary, and each refusal reaches the caller through its own
 * exception handler in this package.
 */
@Controller("/api/admin/organos/taxonomia")
@Secured("ADMIN")
class TaxonomiaAdminController {

  private final CreateTermo createTermo;
  private final RenameTermo renameTermo;
  private final MoveTermo moveTermo;
  private final DeleteTermo deleteTermo;

  TaxonomiaAdminController(CreateTermo createTermo, RenameTermo renameTermo, MoveTermo moveTermo,
      DeleteTermo deleteTermo) {
    this.createTermo = createTermo;
    this.renameTermo = renameTermo;
    this.moveTermo = moveTermo;
    this.deleteTermo = deleteTermo;
  }

  @Post("/termos")
  @Status(HttpStatus.CREATED)
  TermoResponse create(@Valid @Body CreateTermoRequest request) {
    return TermoResponse.of(createTermo.create(request.name(), termoIdOf(request.parentId())));
  }

  @Patch("/termo/{id}")
  TermoResponse rename(@PathVariable UUID id, @Valid @Body RenameTermoRequest request) {
    return TermoResponse.of(renameTermo.rename(new TermoId(id), request.name()));
  }

  @Put("/termo/{id}/parent")
  @Status(HttpStatus.NO_CONTENT)
  void move(@PathVariable UUID id, @Valid @Body MoveTermoRequest request) {
    moveTermo.move(new TermoId(id), termoIdOf(request.parentId()));
  }

  @Delete("/termo/{id}")
  @Status(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    deleteTermo.delete(new TermoId(id));
  }

  private static @Nullable TermoId termoIdOf(@Nullable UUID parentId) {
    return parentId == null ? null : new TermoId(parentId);
  }
}
