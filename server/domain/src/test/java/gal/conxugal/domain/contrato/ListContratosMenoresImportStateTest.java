package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.OrganoId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListContratosMenoresImportStateTest {

  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");
  private static final OrganoId HALF_LOADED = new OrganoId(UUID.randomUUID());
  private static final OrganoId LOADED = new OrganoId(UUID.randomUUID());
  private static final OrganoId NEVER_IMPORTED = new OrganoId(UUID.randomUUID());

  @Mock
  private ContratosMenoresImportStateRepository importStateRepository;

  private ListContratosMenoresImportState listImportState;

  @BeforeEach
  void setUp() {
    listImportState = new ListContratosMenoresImportState(importStateRepository);
  }

  @Test
  void keys_every_stored_state_by_its_organo() {
    when(importStateRepository.findAll()).thenReturn(List.of(
        ContratosMenoresImportState.startedAt(HALF_LOADED, T_ZERO),
        new ContratosMenoresImportState(
            LOADED, ContratosMenoresImportStatus.COMPLETE, LocalDate.of(2018, 1, 1), T_ZERO)));

    Map<OrganoId, ContratosMenoresImportStatus> statuses = listImportState.byOrgano();

    assertThat(statuses)
        .containsOnly(
            entry(HALF_LOADED, ContratosMenoresImportStatus.INCOMPLETE),
            entry(LOADED, ContratosMenoresImportStatus.COMPLETE));
  }

  @Test
  void answers_with_nothing_when_no_organo_has_been_imported() {
    when(importStateRepository.findAll()).thenReturn(List.of());

    assertThat(listImportState.byOrgano()).isEmpty();
  }

  // An Órgano with no row is never started, and reading it that way here is what stops each call
  // site inventing its own reading of a missing entry.
  @Test
  void an_organo_with_no_stored_state_reads_as_never_started() {
    Map<OrganoId, ContratosMenoresImportStatus> statuses =
        Map.of(HALF_LOADED, ContratosMenoresImportStatus.INCOMPLETE);

    assertThat(ListContratosMenoresImportState.statusOf(statuses, NEVER_IMPORTED))
        .isEqualTo(ContratosMenoresImportStatus.NEVER_STARTED);
  }

  @Test
  void an_organo_with_stored_state_reads_as_that_state() {
    Map<OrganoId, ContratosMenoresImportStatus> statuses =
        Map.of(HALF_LOADED, ContratosMenoresImportStatus.INCOMPLETE);

    assertThat(ListContratosMenoresImportState.statusOf(statuses, HALF_LOADED))
        .isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
  }
}
