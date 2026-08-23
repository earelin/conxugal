package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.jdbc.runtime.PreparedStatementCallback;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The plumbing every statement in the licitación adapters runs through. It needs no database: what
 * it does is bind, execute and take the one row a {@code RETURNING} clause promised, and a stubbed
 * {@code JdbcOperations} reaches every branch of that — including the one no live statement can.
 *
 * <p>The five converters are pure and are asserted directly. Each has a present case and an absent
 * one, because absence means something different to every one of them: no lote is <em>the procedure
 * as a whole</em>, no operador is an award that names nobody, and no fiscal identifier is a cell
 * whose trailing token was not identifier-shaped.
 *
 * <p>Nothing is stubbed in a fixture. Ten of these tests touch no mock at all, and under strict
 * stubbing a shared {@code @BeforeEach} would fail every one of them for the stubs they never used.
 */
@ExtendWith(MockitoExtension.class)
class UpsertsTest {

  private static final String SQL = "INSERT INTO licitacion_state (code) VALUES (?) RETURNING id";

  @Mock
  private JdbcOperations jdbcOperations;

  @Mock
  private PreparedStatement statement;

  @Mock
  private ResultSet rows;

  @Test
  void returning_builds_its_value_from_the_row_the_statement_answered() throws SQLException {
    givenTheStatementRuns();
    doReturn(rows).when(statement).executeQuery();
    doReturn(true).when(rows).next();
    doReturn("45000000").when(rows).getString("code");

    String answered =
        Upserts.returning(jdbcOperations, SQL, ignored -> {}, row -> row.getString("code"));

    assertThat(answered).isEqualTo("45000000");
  }

  @Test
  void returning_id_reads_the_identity_the_statement_assigned() throws SQLException {
    givenTheStatementRuns();
    UUID assigned = UUID.fromString("0195e0a4-1c2b-7000-8000-000000000001");
    doReturn(rows).when(statement).executeQuery();
    doReturn(true).when(rows).next();
    doReturn(assigned).when(rows).getObject("id", UUID.class);

    UUID answered = Upserts.returningId(jdbcOperations, SQL, ignored -> {});

    assertThat(answered).isEqualTo(assigned);
  }

  // Bound before the statement runs, which is the ordering the helper exists to guarantee: a
  // binding applied afterwards would execute the statement with none of its parameters set.
  @Test
  void returning_binds_the_parameters_before_it_executes() throws SQLException {
    AtomicBoolean bound = new AtomicBoolean();
    givenTheStatementRuns();
    doAnswer(invocation -> {
      assertThat(bound).isTrue();
      return rows;
    }).when(statement).executeQuery();
    doReturn(true).when(rows).next();
    doReturn("45000000").when(rows).getString("code");

    Upserts.returning(
        jdbcOperations, SQL, ignored -> bound.set(true), row -> row.getString("code"));

    assertThat(bound).isTrue();
  }

  // Unreachable while every statement in the package is an unconditional DO UPDATE over a table
  // with no trigger. It is what would catch a conflict clause that grew a WHERE and started
  // declining rows silently, and a stub is the only way to stand where such a statement would.
  @Test
  void returning_refuses_statement_that_answered_no_row() throws SQLException {
    givenTheStatementRuns();
    doReturn(rows).when(statement).executeQuery();
    doReturn(false).when(rows).next();

    assertThatThrownBy(
            () -> Upserts.returning(jdbcOperations, SQL, ignored -> {}, row -> row.getString("id")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answered no row")
        .hasMessageContaining(SQL);
  }

  // A failure binding a parameter is the statement's failure and travels as one, rather than being
  // softened into a value the caller would have to think to inspect.
  @Test
  void returning_lets_failure_binding_parameter_travel() {
    givenTheStatementRuns();

    assertThatThrownBy(
            () ->
                Upserts.returning(
                    jdbcOperations,
                    SQL,
                    ignored -> {
                      throw new SQLException("no such column");
                    },
                    row -> row.getString("id")))
        .isInstanceOf(SQLException.class)
        .hasMessage("no such column");
  }

  @Test
  void date_of_published_day_is_that_day() {
    assertThat(Upserts.date(LocalDate.of(2026, 3, 14))).isEqualTo(Date.valueOf("2026-03-14"));
  }

  // A date the adapter could not interpret arrives as null and stores as absent, rather than
  // rejecting the procedure that carried it.
  @Test
  void date_of_value_that_could_not_be_interpreted_is_null() {
    assertThat(Upserts.date(null)).isNull();
  }

  // Unwrapped without rounding or rescaling: the column decides the scale it stores.
  @Test
  void amount_is_the_figure_the_money_wraps() {
    assertThat(Upserts.amount(new Money(new BigDecimal("206996.66"))))
        .isEqualTo(new BigDecimal("206996.66"));
  }

  @Test
  void amount_of_figure_the_source_published_none_of_is_null() {
    assertThat(Upserts.amount(null)).isNull();
  }

  @Test
  void lote_is_the_identity_the_lote_was_stored_under() {
    UUID assigned = UUID.fromString("0195e0a4-1c2b-7000-8000-000000000002");

    assertThat(Upserts.lote(new LoteId(assigned))).isEqualTo(assigned);
  }

  // Null is the procedure as a whole — a fact the source publishes — and not a caller's omission.
  @Test
  void lote_of_row_standing_for_the_procedure_as_whole_is_null() {
    assertThat(Upserts.lote(null)).isNull();
  }

  @Test
  void operador_is_the_identity_the_catalogue_holds() {
    UUID assigned = UUID.fromString("0195e0a4-1c2b-7000-8000-000000000003");

    assertThat(Upserts.operador(new OperadorId(assigned))).isEqualTo(assigned);
  }

  // An award that names nobody is a supported outcome rather than a failure.
  @Test
  void operador_of_award_that_names_nobody_is_null() {
    assertThat(Upserts.operador(null)).isNull();
  }

  // Held canonical by the value type, so what reaches the column is the reduced form.
  @Test
  void fiscal_identifier_is_the_canonical_form_of_what_the_cell_carried() {
    assertThat(Upserts.fiscalIdentifier(new FiscalIdentifier("  a41111220 ")))
        .isEqualTo("A41111220");
  }

  @Test
  void fiscal_identifier_of_cell_that_carried_none_is_null() {
    assertThat(Upserts.fiscalIdentifier(null)).isNull();
  }

  /** Hands the callback the statement, which is the whole of what {@code JdbcOperations} does. */
  private void givenTheStatementRuns() {
    doAnswer(invocation -> callbackOf(invocation.getArgument(1)).call(statement))
        .when(jdbcOperations)
        .prepareStatement(anyString(), any());
  }

  @SuppressWarnings("unchecked")
  private static PreparedStatementCallback<Object> callbackOf(Object argument) {
    return (PreparedStatementCallback<Object>) argument;
  }
}
