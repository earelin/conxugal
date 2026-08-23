package gal.conxugal.infrastructure.jdbc.licitacion;

import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;

/**
 * The AssertJ-DB table every test in this package asserts over, built once. Five classes wanted the
 * same four calls, and two of them held it character for character.
 *
 * <p>Ordering is always given, never defaulted: {@code row(n)} is only stable if the rows are
 * ordered by something the test controls, and leaning on {@code uuidv7} insertion order would make
 * a passing assertion a coincidence. {@code columnsToOrder} takes an array rather than varargs,
 * which is the kind of detail worth stating in one place.
 */
final class Tables {

  private Tables() {
  }

  static Table orderedBy(DataSource dataSource, String name, String... columns) {
    Table.Order[] order = new Table.Order[columns.length];
    for (int index = 0; index < columns.length; index++) {
      order[index] = Table.Order.asc(columns[index]);
    }
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table(name)
        .columnsToOrder(order)
        .build();
  }
}
