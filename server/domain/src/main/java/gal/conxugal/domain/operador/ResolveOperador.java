package gal.conxugal.domain.operador;

import jakarta.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a published awardee to the operador it names, cataloguing one if nothing named that
 * identifier before and accounting for the name the publication carried.
 *
 * <p>Every contract family derives its operadores through this class, and there is deliberately
 * only one of it. The rule is subtle in three places — an undated publication ranks last, the rank
 * comparison is a strict win, and a promotion files the name it displaced afterwards rather than
 * before — and a second copy diverging on any of them would show up as an operador displayed under
 * different names depending on which family last touched it.
 *
 * <p><strong>It takes values, not a family's row type</strong>, so a family calls it without
 * borrowing another's source entry. What {@link #resolve} takes is what the <em>ranking</em>
 * derivation needs, which is not the same as everything a family might want to catalogue from.
 *
 * <p><strong>A caller that must catalogue without contributing a rank has a second entry point
 * here, {@link #resolveWithoutRanking}, never a rank engineered to lose.</strong> A losing rank
 * does not express <em>this publication ranks nothing</em>: it still reaches {@code retainName},
 * filing the name among the operador's alternatives, and no port drops a retained name except as a
 * side effect of promoting it — so the mistake is permanent and silent. A losing bid is exactly
 * such a caller.
 *
 * <p><strong>It owns no transaction.</strong> The caller's boundary is the one the writes join, so
 * the operador a contract names is created beside the write that stores the contract and the two
 * commit together — and the second contract of a batch naming a new operador reads what the first
 * wrote.
 */
@Singleton
public class ResolveOperador {

  private final OperadorRepository operadores;

  public ResolveOperador(OperadorRepository operadores) {
    this.operadores = operadores;
  }

  /**
   * The operador this publication names, or nothing when its published identifier is unusable —
   * absent, empty once surrounding whitespace is ignored, or one of the placeholder forms
   * {@link FiscalIdentifier#of} turns away. Nothing is catalogued for such a publication: never a
   * placeholder, and never a shared <em>unknown</em> row that would pool unrelated parties under
   * one identity.
   *
   * <p>A publication that carried <b>no name</b> contributes none: it is catalogued under the empty
   * name if nothing named its identifier before, because an operador has to be displayed as
   * something and inventing one is what R13 forbids — but it never displaces a name that was
   * published, and never enters the retained set, where the empty string is not a name the operador
   * has borne.
   *
   * @param publishedFiscalId the fiscal identifier as published, in whatever spelling
   * @param publishedName the name as published, or null where the source carried none
   * @param rank which publication these values were taken from
   */
  public Optional<OperadorEconomico> resolve(
      @Nullable String publishedFiscalId, @Nullable String publishedName, NomeRank rank) {
    Objects.requireNonNull(rank, "rank must not be null");
    Optional<FiscalIdentifier> published = FiscalIdentifier.of(publishedFiscalId);
    if (published.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(operadorHolding(published.get(), publishedName, rank));
  }

  /**
   * The operador this publication names, catalogued now under the name it published if nothing
   * named that identifier before — and otherwise <strong>left exactly as it stands</strong>.
   * Nothing is promoted, nothing is retained and no rank advances: this publication says who bid,
   * and says nothing about what any operador should be displayed as.
   *
   * <p><strong>Creating is forced, and it is the whole of what a rank-less publication contributes.
   * </strong> SPEC-0006 R3 makes an identifier resolve to an operador or to nobody, and R16 needs
   * the participation to name one, so a bid by a firm no contract has named cannot both be recorded
   * and catalogue nothing. It is catalogued at {@link NomeRank#unranked()}, so the first contract
   * to name that operador takes the display from it.
   *
   * <p><strong>The name such a row was created under never joins the retained set</strong>, not
   * even when a contract later displaces it. {@link #account} reads the sentinel rank off the row
   * and declines to file the name it displaced, so a bid cannot reach the alternatives by the back
   * door either.
   *
   * <p>The identifier arrives already reduced, so a caller holding nothing usable holds null —
   * which is the same answer {@link #resolve} reaches through {@link FiscalIdentifier#of}, from the
   * other end.
   *
   * @param fiscalId the party's identifier, or null where the source published none this type
   *     accepts
   * @param publishedName the name as published, or null where the source carried none
   */
  public Optional<OperadorEconomico> resolveWithoutRanking(
      @Nullable FiscalIdentifier fiscalId, @Nullable String publishedName) {
    if (fiscalId == null) {
      return Optional.empty();
    }
    return Optional.of(
        operadores
            .findByFiscalId(fiscalId)
            .orElseGet(() -> catalogue(fiscalId, publishedName, NomeRank.unranked())));
  }

  /**
   * The operador holding this identifier: catalogued now if nothing named it before, and otherwise
   * the one already held, accounted for against the name this publication carried.
   *
   * <p>Wrapping in {@link Optional#of} rather than mapping over one is deliberate — a store
   * answering null to an insert is a defect worth a thrown exception, not an award quietly recorded
   * under nobody.
   */
  private OperadorEconomico operadorHolding(
      FiscalIdentifier fiscalId, @Nullable String publishedName, NomeRank rank) {
    Optional<OperadorEconomico> catalogued = operadores.findByFiscalId(fiscalId);
    if (catalogued.isEmpty()) {
      return catalogue(fiscalId, publishedName, rank);
    }
    OperadorEconomico incumbent = catalogued.get();
    if (publishedName != null) {
      account(incumbent, publishedName, rank);
    }
    return incumbent;
  }

  /**
   * A catalogue entry for an identifier nothing named before. Both entry points reach it, so the
   * rule that an operador has to be displayed as something — the empty name where the publication
   * carried none, never an invented one — holds however it was catalogued.
   */
  private OperadorEconomico catalogue(
      FiscalIdentifier fiscalId, @Nullable String publishedName, NomeRank rank) {
    return operadores.insert(
        new OperadorEconomico(fiscalId, publishedName == null ? "" : publishedName, rank));
  }

  /**
   * Accounts for the name this publication carried: either it moves into the display and the name
   * it displaced is retained beside the operador, or it is retained itself. One name moves into the
   * retained set every time, so <em>no retained name equals the displayed one</em> holds after
   * every publication — except when the name already displayed is republished, which advances the
   * rank and retains nothing, there being no second name to file, and except when the name being
   * displaced is one no publication ranked.
   *
   * <p><strong>Promoting comes first.</strong> Retaining the displaced name before the promotion
   * would ask the store to file a name that is still the displayed one, which it declines — losing
   * the name silently.
   *
   * <p><strong>A displaced name that stood at {@link NomeRank#unranked()} is not filed.</strong>
   * That rank is only ever written by {@link #resolveWithoutRanking}, so it says the displayed name
   * came from a publication that ranks nothing — a bid — and R15 retains what an operador's
   * <em>contracts</em> published. Filing it would put a bid's name among the alternatives by the
   * back door, which is the outcome the second entry point exists to prevent, and would leave a
   * retained name carrying a rank no publication produced. Nothing is tracked to know this: the
   * sentinel rank on the row <em>is</em> the fact, which is why it costs no column.
   */
  private void account(OperadorEconomico incumbent, String publishedName, NomeRank rank) {
    OperadorId id =
        Objects.requireNonNull(incumbent.id(), "a catalogued operador must carry an identity");
    boolean renaming = !incumbent.name().equals(publishedName);
    if (rank.outranks(incumbent.nameRank())) {
      operadores.promoteName(id, publishedName, rank);
      if (renaming && !NomeRank.unranked().equals(incumbent.nameRank())) {
        operadores.retainName(new NomeAlternativo(id, incumbent.name(), incumbent.nameRank()));
      }
    } else if (renaming) {
      operadores.retainName(new NomeAlternativo(id, publishedName, rank));
    }
  }
}
