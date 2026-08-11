package gal.conxugal.domain.contrato;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeAlternativo;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
 * <p>An operador is looked up again for each contract rather than kept in a map for the batch.
 * Every write above is inside this transaction, so the second contract of a batch naming a new
 * operador reads what the first wrote — and the names an operador has borne are never accumulated
 * in memory, where a name arriving at two ranks would collapse to whichever the set happened to
 * keep.
 *
 * <p>A contract that published <em>no</em> name is stored under the operador its identifier names
 * all the same — identity is the identifier alone, and refusing the award over a value the source
 * left blank would record it under nobody — but it supplies no name to rank. The source is not
 * expected to publish either an award without a name or one without an identifier.
 */
@Singleton
public class StoreContratosMenoresBatch {

  private final OperadorRepository operadores;
  private final ContratoMenorRepository contratos;

  public StoreContratosMenoresBatch(
      OperadorRepository operadores, ContratoMenorRepository contratos) {
    this.operadores = operadores;
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
        operadorAwarded(entry));
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

  /**
   * The operador this award names, or nothing when its published identifier is unusable — absent,
   * or empty once surrounding whitespace is ignored. Such a contract is stored under a null
   * operador: never a placeholder, and never a shared <em>unknown</em> row that would pool
   * unrelated awards under one identity. Because the schema is normalised, it therefore records no
   * awardee at all.
   *
   * <p>An award that published <b>no name</b> contributes none: it is catalogued under the empty
   * name if nothing named its identifier before, because an operador has to be displayed as
   * something and inventing one is what R13 forbids — but it never displaces a name a contract did
   * publish, and never enters the retained set, where the empty string is not a name the operador
   * has borne.
   */
  private @Nullable OperadorEconomico operadorAwarded(ContratoMenorSourceEntry entry) {
    Optional<FiscalIdentifier> published = FiscalIdentifier.of(entry.awardeeFiscalId());
    if (published.isEmpty()) {
      return null;
    }
    FiscalIdentifier fiscalId = published.get();
    String publishedName = entry.awardeeName();
    NomeRank rank = new NomeRank(entry.publicationDate(), entry.sourceId());
    Optional<OperadorEconomico> catalogued = operadores.findByFiscalId(fiscalId);
    if (catalogued.isEmpty()) {
      return operadores.insert(
          new OperadorEconomico(fiscalId, publishedName == null ? "" : publishedName, rank));
    }
    OperadorEconomico incumbent = catalogued.get();
    if (publishedName != null) {
      account(incumbent, publishedName, rank);
    }
    return incumbent;
  }

  /**
   * Accounts for the name this award published: either it moves into the display and the name it
   * displaced is retained beside the operador, or it is retained itself. One name moves into the
   * retained set every time, so <em>no retained name equals the displayed one</em> holds after
   * every contract — except when the award republishes the name already displayed, which advances
   * the rank and retains nothing, there being no second name to file.
   *
   * <p><strong>Promoting comes first.</strong> Retaining the displaced name before the promotion
   * would ask the store to file a name that is still the displayed one, which it declines — losing
   * the name silently.
   */
  private void account(OperadorEconomico incumbent, String publishedName, NomeRank rank) {
    OperadorId id =
        Objects.requireNonNull(incumbent.id(), "a catalogued operador must carry an identity");
    boolean renaming = !incumbent.name().equals(publishedName);
    if (rank.outranks(incumbent.nameRank())) {
      operadores.promoteName(id, publishedName, rank);
      if (renaming) {
        operadores.retainName(new NomeAlternativo(id, incumbent.name(), incumbent.nameRank()));
      }
    } else if (renaming) {
      operadores.retainName(new NomeAlternativo(id, publishedName, rank));
    }
  }
}
