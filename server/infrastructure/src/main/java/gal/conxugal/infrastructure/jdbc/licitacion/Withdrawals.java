package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionId;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

/**
 * The plumbing the six reconciliation statements in this package share: bind the procedure and the
 * children it still publishes, run the update, answer how many rows it newly marked.
 *
 * <p>Its own class rather than a method on {@link Upserts}, whose every helper reads back the one
 * row a {@code RETURNING} clause promised — "an upsert that reaches the database always writes",
 * which is exactly what an update scoped to a set is not.
 *
 * <p><strong>An empty retained collection is the load-bearing case rather than the degenerate
 * one.</strong> A record that publishes no awards at all withdraws every award the procedure
 * holds, and PostgreSQL gives that for free: {@code id = ANY('{}'::uuid[])} is {@code FALSE}
 * rather than null, so the negation holds for every row. No caller needs a branch, and adding a
 * short-circuit here would turn the commonest reconciliation into a silent no-op.
 */
final class Withdrawals {

  private Withdrawals() {
  }

  /**
   * Marks withdrawn every row of {@code licitacionId} whose identity is not among {@code retained}.
   *
   * <p><strong>A null identity in {@code retained} would silently retain everything</strong> —
   * {@code NOT (id = ANY(...))} evaluates to null once the array holds one, and null is not true —
   * so this is unreachable only because every caller passes back the identities its own upserts
   * just answered with. It is the one way these statements fail quietly, which is why it is written
   * down rather than guarded: a guard would suggest the case is expected.
   */
  static <T> int absent(
      JdbcOperations jdbcOperations,
      String sql,
      LicitacionId licitacionId,
      Collection<T> retained,
      Function<T, UUID> identity) {
    UUID[] ids = retained.stream().map(identity).toArray(UUID[]::new);
    return jdbcOperations.prepareStatement(sql, statement -> {
      statement.setObject(1, licitacionId.value());
      statement.setArray(2, statement.getConnection().createArrayOf("uuid", ids));
      return statement.executeUpdate();
    });
  }

  /**
   * The statement each child table withdraws through, which differs only in the table it names.
   *
   * <p>{@code NOT withdrawn} is what keeps a re-import that changes nothing from rewriting rows it
   * marked on an earlier run — every one of them, on every run, for a procedure that has ever lost
   * a child.
   */
  static String statementFor(String table) {
    String statement =
        """
        UPDATE %s
        SET withdrawn = TRUE
        WHERE licitacion_id = ?::uuid
            AND NOT withdrawn
            AND NOT (id = ANY(?::uuid[]))
        """;
    return statement.formatted(table);
  }
}
