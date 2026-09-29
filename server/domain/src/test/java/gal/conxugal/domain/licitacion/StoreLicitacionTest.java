package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.ResolveOperador;
import gal.conxugal.domain.operador.UteMembershipRepository;
import gal.conxugal.domain.organo.OrganoId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What one procedure is assembled from, and the thing that makes the two ways of driving this use
 * case produce one procedure rather than two readings that could disagree.
 *
 * <p>The reconciliation itself is integration-tested: what it asserts is which rows a real database
 * still shows afterwards, and a mock cannot answer that. What belongs here is the assembly — that
 * the four values the record cannot publish come from beside it, that everything else comes from
 * the record whichever source supplied those four, and that a record paired with the wrong entry is
 * refused rather than stored.
 */
@ExtendWith(MockitoExtension.class)
class StoreLicitacionTest {

  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");
  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2012, 5, 4);
  private static final LocalDate MODIFIED_ON = LocalDate.of(2012, 6, 28);
  private static final Money BUDGET = new Money(new BigDecimal("206996.66"));

  @Mock private LicitacionRepository licitacions;
  @Mock private LicitacionStateRepository states;
  @Mock private LicitacionContractTypeRepository contractTypes;
  @Mock private LicitacionProcedureTypeRepository procedureTypes;
  @Mock private LicitacionTramitacionTypeRepository tramitacionTypes;
  @Mock private LoteRepository lotes;
  @Mock private CpvRepository cpvs;
  @Mock private CpvClassificationRepository cpvClassifications;
  @Mock private NutRepository nuts;
  @Mock private NutClassificationRepository nutClassifications;
  @Mock private FormalisationRepository formalisations;
  @Mock private ParticipationRepository participations;
  @Mock private UteMembershipRepository memberships;
  @Mock private AwardRepository awards;
  @Mock private OperadorRepository operadores;

  private @Nullable Licitacion written;

  // The half of the aggregate that exists nowhere on the record: the listing publishes both dates
  // and both halves of the state, and the record publishes the state's label alone -- which for the
  // two codes sharing one is ambiguous.
  @Test
  void takes_both_dates_and_both_halves_of_the_state_from_the_listing_entry() {
    theStoreAccepts();

    store().store(listingEntry(), record(), ORGANO_ID);

    assertThat(written).isNotNull();
    assertThat(written.publicationDate()).isEqualTo(PUBLISHED_ON);
    assertThat(written.lastModified()).isEqualTo(MODIFIED_ON);
    assertThat(written.state().code()).isEqualTo(101);
    assertThat(written.state().label()).isEqualTo("Histórico");
  }

  // The retry path, which arrives with no listing row: the ledger kept exactly those four values,
  // so the procedure it stores is the one the listing-driven store would have stored.
  @Test
  void ledger_driven_store_produces_same_procedure_as_listing_driven_one() {
    theStoreAccepts();
    store().store(listingEntry(), record(), ORGANO_ID);
    Licitacion fromListing = written;

    store().store(ledgerEntry(), record(), ORGANO_ID);

    assertThat(written).isNotNull().usingRecursiveComparison().isEqualTo(fromListing);
  }

  // Everything but those four comes from the record, and it has to: the listing publishes an object
  // and an amount of its own, and taking either would make the two paths disagree.
  @Test
  void takes_every_other_value_from_the_record_even_where_the_listing_publishes_one() {
    theStoreAccepts();

    store().store(listingEntry(), record(), ORGANO_ID);

    assertThat(written).isNotNull();
    assertThat(written.obxecto()).isEqualTo("Servizo de limpeza");
    assertThat(written.baseBudget()).isEqualTo(BUDGET);
    assertThat(written.expediente()).isEqualTo("2012/0042");
  }

  // Nothing downstream would notice the mismatch -- the record supplies the attributes and the
  // entry the dates and the state, and both are plausible on their own.
  @Test
  void refuses_record_that_describes_another_publication() {
    StoreLicitacion store = store();
    LicitacionListingEntry entry = listingEntry();
    LicitacionRecord other = recordOf(new PublicationId("822055"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> store.store(entry, other, ORGANO_ID))
        .withMessageContaining("822055")
        .withMessageContaining("822054");
  }

  private StoreLicitacion store() {
    ResolveOperador resolveOperador = new ResolveOperador(operadores);
    return new StoreLicitacion(
        licitacions,
        states,
        contractTypes,
        procedureTypes,
        tramitacionTypes,
        lotes,
        cpvs,
        cpvClassifications,
        nuts,
        nutClassifications,
        formalisations,
        participations,
        new StoreLicitacionConsortia(resolveOperador, participations, memberships),
        new StoreLicitacionBidders(resolveOperador, participations),
        new StoreLicitacionAwards(resolveOperador, operadores, awards));
  }

  /** The store as it behaves for a procedure it has not held: it assigns an identity. */
  private void theStoreAccepts() {
    when(states.upsert(any()))
        .thenAnswer(
            invocation -> {
              LicitacionState incoming = invocation.getArgument(0);
              return new LicitacionState(
                  new LicitacionStateId(UUID.nameUUIDFromBytes(new byte[] {1})),
                  incoming.code(),
                  incoming.label());
            });
    when(licitacions.upsert(any()))
        .thenAnswer(
            invocation -> {
              written = invocation.getArgument(0);
              return new UpsertOutcome(
                  new LicitacionId(UUID.nameUUIDFromBytes(new byte[] {2})), UpsertOperation.ADDED);
            });
  }

  private static LicitacionListingEntry listingEntry() {
    // The object and the amount published here are deliberately not the record's: the assertion
    // above is that they are ignored.
    return new LicitacionListingEntry(
        PUBLICATION_ID,
        PUBLISHED_ON,
        MODIFIED_ON,
        "Limpeza — como a listaxe o resume",
        new Money(new BigDecimal("1.00")),
        101,
        "Histórico");
  }

  private static LicitacionOutstandingRecord ledgerEntry() {
    return new LicitacionOutstandingRecord(
        PUBLICATION_ID, PUBLISHED_ON, MODIFIED_ON, 101, "Histórico");
  }

  private static LicitacionRecord record() {
    return recordOf(PUBLICATION_ID);
  }

  private static LicitacionRecord recordOf(PublicationId publicationId) {
    return new LicitacionRecord(
        publicationId,
        "2012/0042",
        "Servizo de limpeza",
        null,
        null,
        null,
        null,
        new PublishedAmount(BUDGET, VatBasis.INCLUSIVE),
        null,
        "Histórico",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
