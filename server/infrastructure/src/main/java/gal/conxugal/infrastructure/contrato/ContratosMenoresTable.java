package gal.conxugal.infrastructure.contrato;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The source's contratos menores table response: the rows of the requested page, and the Órgano's
 * whole published count.
 *
 * <p>Both fields are boxed so that a response missing either is distinguishable from one reporting
 * nothing — the first is a body that is not the documented shape, the second an ordinary empty
 * page.
 */
@Serdeable.Deserializable
record ContratosMenoresTable(
    @Nullable Long recordsTotal, @Nullable List<ContratosMenoresRow> data) {}
