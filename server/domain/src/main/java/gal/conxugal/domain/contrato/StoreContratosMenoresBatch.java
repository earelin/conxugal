package gal.conxugal.domain.contrato;

import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.ResolveOperador;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores one batch of published contratos menores together with the operadores they were awarded
 * to: every contract is resolved to its operador and written holding a reference to it, and the
 * two commit together.
 *
 * <p><strong>The awardee comes from the source row, never from the stored contract.</strong> The
 * schema is normalised — a {@link ContratoMenor} keeps no published name and no published
 * identifier — so the values matched on here are in hand only while the batch is being imported. A
 * derivation running over already-stored contracts would find nothing to derive from, which is why
 * this step lives inside the import and can never be a backfill.
 *
 * <p><strong>One transaction.</strong> The operador a contract names is created, promoted and
 * retained beside the write that stores the contract, so a crash cannot leave a stored contract
 * whose operador was never created. Replaying the batch afterwards changes nothing: the contract
 * matches on its source identifier, the operador on its canonical fiscal identifier, and the rank
 * comparison is a strict win, so nothing is duplicated and no name flaps.
 *
 * <p><strong>Every contract is resolved, whether it is being inserted or refreshed.</strong> That
 * is what makes a correction changing a contract's published identifier repoint its reference to
 * the operador the corrected identifier names, creating that operador if no contract named it
 * before.
 *
 * <p>An operador is resolved again for each contract rather than kept in a map for the batch.
 * Every write the derivation makes is inside this transaction, so the second contract of a batch
 * naming a new operador reads what the first wrote — and the names an operador has borne are never
 * accumulated in memory, where a name arriving at two ranks would collapse to whichever the set
 * happened to keep.
 *
 * <p><strong>What a contract supplies to the derivation is a rank</strong> — its publication date
 * and its source identifier, this family's contract identity. What becomes of the identifier and
 * the name beside it is {@link ResolveOperador}'s, and shared with every other family, so an award
 * whose identifier is unusable yields no operador here for the same reason it does there.
 */
@Singleton
public class StoreContratosMenoresBatch {

  private final ResolveOperador resolveOperador;
  private final ContratoMenorRepository contratos;

  public StoreContratosMenoresBatch(
      ResolveOperador resolveOperador, ContratoMenorRepository contratos) {
    this.resolveOperador = resolveOperador;
    this.contratos = contratos;
  }

  /**
   * Resolves every entry to its operador and stores the batch, answering the counts the store
   * reports.
   *
   * @param entries one page as the source published it, awardees included
   * @param organoId the Órgano whose history is being walked
   */
  @Transactional
  public UpsertCounts store(List<ContratoMenorSourceEntry> entries, OrganoId organoId) {
    Objects.requireNonNull(entries, "entries must not be null");
    Objects.requireNonNull(organoId, "organoId must not be null");
    Collection<ContratoMenorSourceEntry> page = lastReadingPerSourceId(entries);
    List<ContratoMenor> batch = new ArrayList<>(page.size());
    for (ContratoMenorSourceEntry entry : page) {
      batch.add(contratoOf(entry, organoId));
    }
    return contratos.upsertAll(batch);
  }

  /** One published award as the contract this Órgano stores, awardee resolved. */
  private ContratoMenor contratoOf(ContratoMenorSourceEntry entry, OrganoId organoId) {
    return new ContratoMenor(
        entry.sourceId(),
        organoId,
        entry.publicationDate(),
        entry.obxecto(),
        entry.amount(),
        entry.duration(),
        resolveOperador
            .resolve(
                entry.awardeeFiscalId(),
                entry.awardeeName(),
                new NomeRank(entry.publicationDate(), entry.sourceId()))
            .orElse(null));
  }

  /**
   * The page with a repeated publication collapsed to its last reading — the rule the store already
   * applies to the contracts themselves, applied here too because the derivation runs first. A
   * reading the store is about to discard would otherwise leave the operador it named behind,
   * catalogued from an award no stored contract points at.
   */
  private static Collection<ContratoMenorSourceEntry> lastReadingPerSourceId(
      Iterable<ContratoMenorSourceEntry> entries) {
    Map<Long, ContratoMenorSourceEntry> bySourceId = new LinkedHashMap<>();
    for (ContratoMenorSourceEntry entry : entries) {
      bySourceId.put(entry.sourceId(), entry);
    }
    return bySourceId.values();
  }
}
