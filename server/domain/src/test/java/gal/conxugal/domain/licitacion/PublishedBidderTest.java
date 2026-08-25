package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.operador.FiscalIdentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the two kinds of bidder guarantee about themselves, as opposed to how a parse decides which
 * kind a row is — that decision is the adapter's, and is asserted against the source's own markup.
 *
 * <p>Both reduce their own lote cell rather than trusting the parse to have done it, so a caller
 * that hands over a raw cell cannot produce a row that silently fails to join against an award.
 */
class PublishedBidderTest {

  @Test
  void reduces_the_zero_padded_lote_cell_the_single_firm_row_was_handed() {
    assertThat(firm("05").loteKey()).isEqualTo("5");
  }

  @Test
  void reduces_the_zero_padded_lote_cell_the_consortium_row_was_handed() {
    assertThat(consortium("05").loteKey()).isEqualTo("5");
  }

  /**
   * <strong>The bidder table writes {@code -} where the award table writes {@code _}</strong>, and
   * both reduce to the same absence — which is the whole reason the cross-check joins on the
   * reduction rather than on the cell.
   */
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"-", "_", "", "   "})
  void holds_the_whole_procedure_for_the_cell_that_names_no_lote(String cell) {
    assertThat(firm(cell).loteKey()).isNull();
    assertThat(consortium(cell).loteKey()).isNull();
  }

  @Test
  void answers_nothing_for_the_name_that_carried_only_whitespace() {
    assertThat(new PublishedBidder.SingleFirm(null, "  ", null).name()).isNull();
    assertThat(new PublishedBidder.Consortium(null, "\n\t", null, List.of()).name()).isNull();
  }

  /**
   * A name is held exactly as it arrives — the row reduces its lote key and nothing else. Trimming
   * is the parse's, and a double space the source published is the source's own content.
   */
  @Test
  void keeps_the_internal_spacing_the_published_name_carried() {
    assertThat(new PublishedBidder.SingleFirm(null, "CIVIS GLOBAL  S L", null).name())
        .isEqualTo("CIVIS GLOBAL  S L");
  }

  /**
   * A caller keeping the list it handed over must not be able to change the consortium's membership
   * afterwards: the store reads it more than once, and a row that could be edited behind it would
   * be a different consortium on the second read.
   */
  @Test
  void copies_the_membership_it_was_handed() {
    List<PublishedConsortiumMember> members = new ArrayList<>();
    members.add(new PublishedConsortiumMember("PRACE", new FiscalIdentifier("A70319678")));
    PublishedBidder.Consortium bidder = new PublishedBidder.Consortium("1", "UTE", null, members);

    members.clear();

    assertThat(bidder.members()).hasSize(1);
  }

  /**
   * <strong>The consortium's identifier is a field of its own</strong>, and the sealed pair is what
   * says so. A caller holding a consortium cannot read its {@code U…} as a firm's, and a caller
   * holding a single firm has no membership to read — which is the confusion an earlier model of
   * this made and the reason the two are different types rather than one with a flag.
   */
  @Test
  void keeps_the_consortium_identifier_apart_from_the_single_firm_identifier() {
    PublishedBidder consortium =
        new PublishedBidder.Consortium(
            "1", "UTE PRACE-TABOADA RAMOS", new FiscalIdentifier("U86486669"), List.of());

    assertThat(consortium).isNotInstanceOf(PublishedBidder.SingleFirm.class);
    assertThat(((PublishedBidder.Consortium) consortium).fiscalIdentifier())
        .isEqualTo(new FiscalIdentifier("U86486669"));
  }

  private static PublishedBidder.SingleFirm firm(String loteCell) {
    return new PublishedBidder.SingleFirm(loteCell, "ACME, S.L.", null);
  }

  private static PublishedBidder.Consortium consortium(String loteCell) {
    return new PublishedBidder.Consortium(loteCell, "UTE ACME-BETA", null, List.of());
  }
}
