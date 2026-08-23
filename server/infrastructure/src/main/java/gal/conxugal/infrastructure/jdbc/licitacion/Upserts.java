package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The plumbing every statement in this package shares: bind, execute, take the one row the
 * {@code RETURNING} clause promised. Shared because each of the twelve upserts here would otherwise
 * repeat it verbatim, and because "answered no row" is a bug in the statement rather than an
 * outcome a caller should have to consider — an upsert that reaches the database always writes.
 *
 * <p>The four converters exist for the same reason: a domain value type reaches JDBC as null or as
 * the thing it wraps, and spelling that ternary out at every binding site is where one of them
 * eventually gets it wrong.
 */
final class Upserts {

  private Upserts() {
  }

  /** Binds a statement's parameters, in the order the statement declares them. */
  @FunctionalInterface
  interface Binding {
    void bind(PreparedStatement statement) throws SQLException;
  }

  /** Builds a value out of the row the statement answered with. */
  @FunctionalInterface
  interface Row<T> {
    T of(ResultSet rows) throws SQLException;
  }

  static <T> T returning(JdbcOperations jdbcOperations, String sql, Binding binding, Row<T> row) {
    return jdbcOperations.prepareStatement(sql, statement -> {
      binding.bind(statement);
      try (ResultSet rows = statement.executeQuery()) {
        // Unreachable while every statement here is an unconditional DO UPDATE over a table with
        // no trigger, which always returns its one row. Stated rather than covered: it is what
        // would catch a conflict clause that grew a WHERE and started declining rows silently.
        if (!rows.next()) {
          throw new IllegalStateException("An upsert answered no row: %s".formatted(sql));
        }
        return row.of(rows);
      }
    });
  }

  /** The identity the statement assigned or found, for the upserts that need nothing else back. */
  static UUID returningId(JdbcOperations jdbcOperations, String sql, Binding binding) {
    return returning(jdbcOperations, sql, binding, rows -> rows.getObject("id", UUID.class));
  }

  static @Nullable Date date(@Nullable LocalDate date) {
    return date == null ? null : Date.valueOf(date);
  }

  static @Nullable BigDecimal amount(@Nullable Money amount) {
    return amount == null ? null : amount.value();
  }

  /**
   * The lote a child hangs off, where it hangs off one. A null is <em>the procedure as a
   * whole</em> — a fact the source publishes, not a caller's omission — which is why this is a
   * plain conversion and not one of the identity guards.
   */
  static @Nullable UUID lote(@Nullable LoteId loteId) {
    return loteId == null ? null : loteId.value();
  }

  /**
   * The operador a row names, where it names one. A null is a party the catalogue does not hold —
   * an award that names nobody is a supported outcome — rather than an operador that was not
   * stored, which the entity's own identity check would have refused first.
   */
  static @Nullable UUID operador(@Nullable OperadorId operadorId) {
    return operadorId == null ? null : operadorId.value();
  }

  /** The identifier a published cell carried, where its trailing token was identifier-shaped. */
  static @Nullable String fiscalIdentifier(@Nullable FiscalIdentifier fiscalIdentifier) {
    return fiscalIdentifier == null ? null : fiscalIdentifier.value();
  }
}
