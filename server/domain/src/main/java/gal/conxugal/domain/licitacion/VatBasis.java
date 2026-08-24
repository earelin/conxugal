package gal.conxugal.domain.licitacion;

/**
 * Whether a published amount includes value-added tax, as the source itself labelled it. The record
 * writes {@code con IVE} or {@code sen IVE} beside its base budget and its estimated value, which
 * makes the basis a fact the source states rather than one this system asserts.
 *
 * <p>It is carried beside the figure rather than folded into it — an amount and the basis it was
 * quoted on are two published values, and reading the marker off the number is what stops
 * {@code 3.378.552,09 con IVE} being parsed as a number at all.
 *
 * <p>Absent where the source marked nothing, which is the ordinary case for an awarded amount: the
 * resolution table's {@code Importe} carries no marker on any measured row.
 */
public enum VatBasis {

  /** {@code con IVE} — the figure includes value-added tax. */
  INCLUSIVE,

  /** {@code sen IVE} — the figure excludes it. */
  EXCLUSIVE
}
