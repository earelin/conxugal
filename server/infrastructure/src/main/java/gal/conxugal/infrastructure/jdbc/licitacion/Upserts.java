package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.money.Money;
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
 * {@code RETURNING} clause promised. Shared because each of the eleven upserts here would otherwise
 * repeat it verbatim, and because "answered no row" is a bug in the statement rather than an
 * outcome a caller should have to consider — an upsert that reaches the database always writes.
 *
 * <p>The three converters exist for the same reason: a domain value type reaches JDBC as null or as
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
}
