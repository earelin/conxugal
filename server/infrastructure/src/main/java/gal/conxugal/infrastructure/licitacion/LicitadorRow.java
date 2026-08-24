package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedConsortiumMember;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

/**
 * One row of {@code Relación de licitadores presentados}, and the one decision this whole feature
 * most depends on getting right: <strong>a bidder is a consortium because of its markup, never
 * because of its name and never because of its identifier.</strong>
 *
 * <p>A consortium's {@code Nome} cell nests a second {@code <ul>} inside the first, naming each
 * member with its own identifier:
 *
 * <pre>{@code
 * <ul class='list-unstyled'>
 *   <li>UTE PRACE-TABOADA RAMOS</li>
 *   <ul>
 *     <li>A70319678 - PRACE SERVICIOS Y OBRAS SA</li>
 *     <li>B94181807 - CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU</li>
 *   </ul>
 * </ul>
 * }</pre>
 *
 * <p>Measured over <strong>613 bidder rows in 250 procedures</strong>, that structural test was
 * <strong>exact</strong> — never firing on a single firm, never missing a consortium. The
 * alternatives are not close: a name test beginning {@code UTE} misses <strong>7 of 35</strong>
 * ({@code MISTURAS-INGESAN} among them), and the {@code U}-prefix identifier test misses
 * <strong>33 of 35</strong>. This is not the inference SPEC-0006 R6 forbids — the markup
 * <em>is</em> the publication, which is precisely what R17's own <em>membership is published, not
 * inferred</em> asks for.
 *
 * <p><strong>The branch is taken before the {@code NIF} cell is read at all, and that ordering is
 * load-bearing.</strong> {@code -} and {@code TEMP-…} appeared on 33 of 35 consortium rows and
 * <strong>0 of 578</strong> single-firm rows; because the structural branch fires first, a
 * placeholder is never offered as a firm's identifier, which would otherwise catalogue one operador
 * holding the identifier {@code -} for dozens of unrelated consortia.
 *
 * <p>Nothing here resolves, mints or catalogues anything. It answers what the source published — a
 * party, its optional identifier, and its members where it has them.
 */
final class LicitadorRow {

  /** The class {@link PublishedValues} and {@code Whitespace} both call blank. */
  private static final String BLANK = "[\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}]";

  /**
   * A member entry: the identifier, a spaced hyphen, then the name — the opposite order from
   * {@code ContratistaCell}'s, which is why the two split on opposite ends and share only the
   * judgement of what an identifier looks like.
   *
   * <p><strong>The hyphen must be spaced on both sides.</strong> Company names carry bare hyphens
   * ({@code UTE PRACE-TABOADA RAMOS}, {@code MISTURAS-INGESAN}), so an unspaced split would file
   * {@code MISTURAS-INGESAN} under a head that is not an identifier at all. The measured separator
   * is {@code " - "} on all 80 member entries.
   */
  private static final Pattern IDENTIFIER_AND_NAME =
      Pattern.compile(
          "^(?<token>"
              + PublishedIdentifier.TOKEN
              + ")"
              + BLANK
              + "+-"
              + BLANK
              + "+(?<name>.*[^\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}])$",
          Pattern.DOTALL);

  private LicitadorRow() {}

  /**
   * The bidder this row published.
   *
   * @param loteCell the row's {@code Lote} cell — {@code 1} on the consortium row above, where the
   *     {@code -} is its <em>NIF</em>; the two are easy to confuse and an earlier draft of this
   *     feature did
   * @param nifCell the row's {@code NIF} cell, read only once the party's kind is known
   * @param nomeCell the row's {@code Nome} cell as markup, whose structure is the classification
   */
  static PublishedBidder read(
      @Nullable String loteCell, @Nullable String nifCell, @Nullable Element nomeCell) {
    Element list = nomeCell == null ? null : nomeCell.selectFirst("ul");
    // jsoup keeps the inner list a child of the outer one, so this is the nested <ul> and never
    // the outer itself — Element.select matches the element it is called on as well as its
    // descendants, which is what makes the child combinator necessary rather than tidy.
    Element memberList = list == null ? null : list.selectFirst("> ul");
    String name = nameOf(nomeCell, list);
    if (memberList == null) {
      // Held exactly as published. FiscalIdentifier's own rule is that nothing beyond emptiness
      // disqualifies one, because the source publishes irregular but genuine identifiers and
      // rejecting them would discard real bids.
      return new PublishedBidder.SingleFirm(
          loteCell, name, FiscalIdentifier.of(nifCell).orElse(null));
    }
    return new PublishedBidder.Consortium(
        loteCell, name, consortiumIdentifier(nifCell), membersOf(memberList));
  }

  /**
   * The consortium's own identifier, which 2 of the 35 measured rows published and 33 did not.
   *
   * <p><strong>The shape is judged here and deliberately is not on a single firm's cell.</strong>
   * The asymmetry is not caution about odd identifiers — it is that the two cells decide different
   * things. A single firm's identifier files that firm under itself, so an irregular but genuine
   * one costs nothing to keep and rejecting it would discard a real bid. A consortium's mints a
   * <em>new</em> catalogue identity from a cell the source is measured to fill with {@code -} or
   * {@code TEMP-…} 33 times in 35 — and one operador holding {@code -} would pool dozens of
   * unrelated consortia under a single identity, which is the harm the whole structural branch
   * exists to prevent.
   *
   * <p>{@link PublishedIdentifier} rejects both placeholders on shape alone: {@code -} on its
   * length, {@code TEMP-00934} on its hyphen. Nothing here knows what a placeholder is.
   */
  private static @Nullable FiscalIdentifier consortiumIdentifier(@Nullable String nifCell) {
    String cell = PublishedValues.text(nifCell);
    if (cell == null || !PublishedIdentifier.isIdentifierShaped(cell)) {
      return null;
    }
    return FiscalIdentifier.of(cell).orElse(null);
  }

  /**
   * The party's own name: the outer list's first item, read as <em>its own</em> text so that a
   * consortium's members can never leak into the name that identifies it. A cell publishing no list
   * at all falls back to the whole cell, which no captured row does but which costs nothing to
   * survive.
   */
  private static @Nullable String nameOf(@Nullable Element cell, @Nullable Element list) {
    Element first = list == null ? null : list.selectFirst("> li");
    if (first != null) {
      return PublishedValues.text(first.wholeOwnText());
    }
    return cell == null ? null : PublishedValues.text(cell.wholeText());
  }

  /**
   * The member firms the nested list names, in the order it named them.
   *
   * <p>An entry that does not split into an identifier and a name yields the whole entry as a name
   * and no identifier, on {@code ContratistaCell}'s failure direction: a member that resolves to
   * nobody costs one membership, whereas a member resolved to the wrong operador corrupts the
   * catalogue. All 80 measured entries split.
   */
  private static List<PublishedConsortiumMember> membersOf(Element memberList) {
    List<PublishedConsortiumMember> members = new ArrayList<>();
    for (Element entry : memberList.select("> li")) {
      members.add(memberOf(PublishedValues.text(entry.wholeText())));
    }
    return members;
  }

  private static PublishedConsortiumMember memberOf(@Nullable String entry) {
    if (entry == null) {
      return new PublishedConsortiumMember(null, null);
    }
    Matcher matcher = IDENTIFIER_AND_NAME.matcher(entry);
    if (!matcher.matches() || !PublishedIdentifier.isIdentifierShaped(matcher.group("token"))) {
      return new PublishedConsortiumMember(entry, null);
    }
    return new PublishedConsortiumMember(
        matcher.group("name"), FiscalIdentifier.of(matcher.group("token")).orElse(null));
  }
}
