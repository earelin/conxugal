package gal.conxugal.infrastructure.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.ContratosMenoresImportConfiguration;
import gal.conxugal.domain.contrato.ImportOrganoContratosMenores;
import gal.conxugal.domain.contrato.ReadContratosMenoresWindow;
import gal.conxugal.domain.contrato.StoreContratosMenoresBatch;
import gal.conxugal.domain.contrato.UpsertCounts;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import io.micronaut.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The walk and the adapter hold one limit between them — the source answers at most three months —
 * and each states it in its own direction: the walk steps backwards from a window's end, the
 * adapter measures forwards from its start. Nothing else makes the two agree, so this drives the
 * real adapter's slice validation with the real walk's windows.
 *
 * <p>It exists because they once did not agree. Stepping back by calendar months lands on a shorter
 * month and stays there, so a walk from {@code 2026-05-31} asked for a window starting
 * {@code 2026-02-28}, whose own three months ended three days before that window did — 92 days the
 * source would have answered, refused by arithmetic alone, and refused identically on every
 * resumption because the cursor kept pointing at it. Both halves passed their own tests: each
 * mocked the other.
 *
 * <p>No database and no HTTP: the walk's ports are stubbed and the adapter's client is mocked, so
 * what is under test is the arithmetic where the two meet.
 */
class ContratosMenoresWalkSliceLimitsTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final String SOURCE_KEY = "242";
  private static final LocalDate HISTORY_FLOOR = LocalDate.of(2018, 1, 1);

  private final ContratosMenoresClient contratosMenoresClient = mock(ContratosMenoresClient.class);
  private final ContratoMenorRepository contratos = mock(ContratoMenorRepository.class);
  private final OperadorRepository operadores = mock(OperadorRepository.class);
  private final ContratosMenoresImportStateRepository importStates =
      mock(ContratosMenoresImportStateRepository.class);
  private final ImportRunRepository importRuns = mock(ImportRunRepository.class);

  private LocalDate resumePoint = HISTORY_FLOOR;

  /**
   * Every answer is an empty page against a count the walk can never reach, so it walks to the
   * history floor and asks for every window between the two.
   */
  @BeforeEach
  void theSourceAnswersEveryWindowWithNothing() {
    when(contratosMenoresClient.table(
            anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
        .thenReturn(HttpResponse.ok(new ContratosMenoresTable(99L, List.of())));
    when(contratos.upsertAll(any())).thenReturn(new UpsertCounts(0, 0));
    when(contratos.countByOrganoId(ORGANO_ID)).thenReturn(0L);
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
    when(importStates.findByOrganoId(ORGANO_ID))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new ContratosMenoresImportState(
                        ORGANO_ID,
                        ContratosMenoresImportStatus.INCOMPLETE,
                        resumePoint,
                        Instant.EPOCH,
                        null)));
  }

  // Every start day of a leap year and of the year after it, each walked back to the floor — so
  // every month length the backwards step can land on is covered, from every start.
  @Test
  void no_start_day_leads_the_walk_into_windows_the_adapter_refuses() {
    List<LocalDate> refused =
        Stream.iterate(LocalDate.of(2024, 1, 1), day -> day.getYear() < 2026, day ->
                day.plusDays(1))
            .filter(this::theAdapterRefusesSomeWindowFrom)
            .toList();

    assertThat(refused).isEmpty();
  }

  private boolean theAdapterRefusesSomeWindowFrom(LocalDate startDay) {
    resumePoint = startDay;
    try {
      walk().run(RUN_ID, organo(), () -> true);
      return false;
    } catch (IllegalArgumentException e) {
      return true;
    }
  }

  private ImportOrganoContratosMenores walk() {
    return new ImportOrganoContratosMenores(
        new ReadContratosMenoresWindow(
            new ContratosDeGaliciaContratoMenorSourceAdapter(contratosMenoresClient),
            new StoreContratosMenoresBatch(operadores, contratos),
            importRuns),
        contratos,
        importStates,
        (Clock) Instant::now,
        new ContratosMenoresImportConfiguration(HISTORY_FLOOR));
  }

  private static OrganoDeContratacion organo() {
    return new OrganoDeContratacion(
        ORGANO_ID, SOURCE_KEY, "Axencia Turismo de Galicia", true, true, null);
  }
}
