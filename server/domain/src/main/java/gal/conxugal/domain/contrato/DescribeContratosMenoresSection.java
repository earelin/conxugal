package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * Everything an Órgano's contratos menores section decides about itself, answered before a single
 * contract is fetched: whether the section exists at all, which years it offers, and the two
 * statements a reader is owed about the data behind them.
 *
 * <p><b>Presence is derived, not asserted.</b> Nothing is answered when the Órgano has no visible
 * contrato menor, and that is the whole mechanism — there is no flag saying whether it has any,
 * and so nothing that could disagree with the years beside it. It is also what makes an Órgano
 * holding <em>only</em> anomalous contracts indistinguishable from one holding none, which is what
 * the withholding rule requires of every surface: to a reader there is nothing to withhold.
 *
 * <p><b>The two flags are produced only on that branch</b>, and the narrowing is deliberate rather
 * than incidental. <em>Is this Órgano imported at all</em> is an administrator's question; it stays
 * unanswerable here because an Órgano with no visible contract is answered with nothing, flags
 * included. What a reader of an existing section learns is only what a reader of an existing
 * section needs: that it is incomplete, and that it is no longer being refreshed.
 *
 * <p><b>Two repositories, because the catalogue read already carries the import state.</b> An
 * Órgano is loaded with its state on the same left join every other reader of it uses, so both
 * flags come off the aggregate — {@link OrganoDeContratacion#importStatus()} already reads a
 * missing state row as {@link ContratosMenoresImportStatus#NEVER_STARTED}, and
 * {@link OrganoDeContratacion#eligibleForImport()} already answers <em>active and marked</em> as
 * one fact rather than two. Reading the state separately would restate both, and give this use
 * case its own opinion about what a missing row means.
 */
@Singleton
public class DescribeContratosMenoresSection {

  private final OrganoRepository organos;
  private final VisibleContratoMenorRepository visibleContratos;

  public DescribeContratosMenoresSection(
      OrganoRepository organos, VisibleContratoMenorRepository visibleContratos) {
    this.organos = organos;
    this.visibleContratos = visibleContratos;
  }

  /**
   * The section this Órgano presents, or nothing at all when it presents none. An Órgano that does
   * not exist is answered the same way as one holding nothing: this read draws no distinction the
   * catalogue does not already publish, and refusing here would say more about an unknown
   * identifier than an empty one does.
   *
   * <p><b>It is two reads and they take no shared snapshot</b>, the same trade-off the paged read
   * makes between its page and its count. An import committing between them can leave the flags
   * describing a state very slightly later than the years beside them — a section that says it is
   * partial while already offering the year that completed it, which is a section reporting itself
   * more cautiously than it needed to rather than anything a reader can be misled by. Holding both
   * in one transaction would buy nothing a reader would notice and would put a transaction
   * boundary on a read the page above this one does not have either.
   */
  public Optional<ContratosMenoresSection> describe(OrganoId organoId) {
    return organos.findById(organoId).flatMap(organo -> sectionOf(organoId, organo));
  }

  private Optional<ContratosMenoresSection> sectionOf(
      OrganoId organoId, OrganoDeContratacion organo) {
    List<YearSelection> years = visibleContratos.years(organoId);
    if (years.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new ContratosMenoresSection(years, partial(organo), organo.eligibleForImport()));
  }

  /** Incomplete covers never started too, there being no stored value standing for that. */
  private static boolean partial(OrganoDeContratacion organo) {
    return organo.importStatus() != ContratosMenoresImportStatus.COMPLETE;
  }
}
