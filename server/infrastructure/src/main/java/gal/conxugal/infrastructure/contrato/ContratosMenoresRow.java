package gal.conxugal.infrastructure.contrato;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * One row of the source's contratos menores table, named and shaped as the source publishes it.
 *
 * <p>{@code importe} binds as a {@link BigDecimal} rather than a floating-point number: the scale
 * the source published is part of the amount, and a {@code double} would lose it before the value
 * ever reached a {@code Money}.
 *
 * <p>{@code nif} and {@code adjudicatario} arrive padded out to fixed widths; nothing here undoes
 * that, because normalising is the adapter's single responsibility. {@code draw} and {@code
 * recordsFiltered} are not bound — nothing above the port reads either.
 */
@Serdeable.Deserializable
record ContratosMenoresRow(
    @Nullable Long id,
    @Nullable String publicado,
    @Nullable String objeto,
    @Nullable BigDecimal importe,
    @Nullable String nif,
    @Nullable String adjudicatario,
    @Nullable String duracion) {}
