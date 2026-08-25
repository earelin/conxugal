import { groupBy, uniq } from 'es-toolkit';

import { formatDateTime } from '../../../shared/lib/date';
import { isProblemType } from '../../../shared/lib/httpError';
import { counted } from '../../../shared/lib/plural';
import { strings } from '../../../shared/lib/strings';
import type { ImportRefusalKind, ImportRun, ImportRunOrgano } from './contratosMenores';

const copy = strings.admin.organos.contratosMenores;

/** The two refusals as the triggers name them, which is by type and never by status. */
const PROBLEM_TYPE = {
  IMPORT_ALREADY_RUNNING: 'urn:conxugal:problem-type:import-already-running',
  ORGANO_NOT_ELIGIBLE: 'urn:conxugal:problem-type:organo-not-eligible',
} as const;

const RUN_NOT_FOUND = 'urn:conxugal:problem-type:import-run-not-found';

/**
 * How a banner is coloured. Only `failure` is red: a refused import is an
 * outcome, and a run that stopped advancing is not a fault of the request.
 */
export type RunTone = 'progress' | 'success' | 'partial' | 'failure' | 'abandoned' | 'unknown';

/** One covered Órgano that failed, kept beside the identity that names it. */
export interface RunFailure {
  organoId: string;
  line: string;
}

export interface RunReport {
  tone: RunTone;
  title: string;
  /** Órganos and contracts, never shown for a refusal — there are none. */
  counts: string;
  failuresTitle: string | null;
  failures: RunFailure[];
  timing: string;
  note: string | null;
}

export interface RefusalReport {
  title: string;
  message: string;
  note: string;
  /**
   * Whether asking again could answer differently. The guard clears on its own;
   * an ineligible Órgano stays ineligible until the catalogue or the mark moves.
   */
  retryable: boolean;
}

/**
 * Dated, not just clocked. A first import runs for days, so the moment a run
 * started is routinely read on a later one, and a bare `06:14` would be taken
 * for this morning.
 */
function at(iso: string): string {
  return formatDateTime(iso);
}

function contractCounts(run: ImportRun): string {
  return `${counted(run.added, copy.run.added)} · ${counted(run.refreshed, copy.run.refreshed)}`;
}

/**
 * The coverage as one entry per Órgano rather than per row. A run covering an
 * Órgano in both families holds two rows for it, and every figure this banner
 * shows is a count of Órganos — so counting the array itself would say six for
 * three.
 */
function byOrgano(run: ImportRun): ImportRunOrgano[][] {
  return Object.values(groupBy(run.coveredOrganos, (organo) => organo.organoId));
}

function organosCovered(run: ImportRun): number {
  return byOrgano(run).length;
}

function completedOf(run: ImportRun): string {
  const organos = byOrgano(run);
  // Every family has to have succeeded: a mixed Órgano is not completed, which
  // is the rule the run's own PARTIALLY_SUCCEEDED verdict follows.
  const completed = organos.filter((rows) =>
    rows.every((organo) => organo.state === 'SUCCEEDED'),
  ).length;
  return copy.run.completedOf(completed, organos.length);
}

function failureLines(run: ImportRun, nameOf: (organoId: string) => string): RunFailure[] {
  const failed = groupBy(
    run.coveredOrganos.filter((organo) => organo.state === 'FAILED'),
    (organo) => organo.organoId,
  );
  // One entry per Órgano, not per family: the title above this list counts the
  // entries, and an Órgano that failed in both families is still one Órgano.
  // Its reasons ride on the single line rather than one of them being lost —
  // deduplicated, because both families read the same source through the same
  // failure mapping, so one outage gives them the identical reason and the line
  // carries nothing to tell the two halves apart.
  return Object.entries(failed).map(([organoId, rows]) => ({
    organoId,
    line: [nameOf(organoId), ...uniq(rows.flatMap((organo) => organo.failureReason ?? []))].join(
      ' · ',
    ),
  }));
}

function timing(run: ImportRun): string {
  const started = `${copy.run.startedAtPrefix} ${at(run.startedAt)}`;
  if (run.state === 'IN_PROGRESS') {
    return started;
  }
  const ended =
    run.finishedAt === null
      ? copy.run.unfinished
      : `${copy.run.finishedAtPrefix} ${at(run.finishedAt)}`;
  return `${started} · ${ended}`;
}

/**
 * One run as the banner renders it.
 *
 * `nameOf` resolves a covered Órgano against the catalogue the section already
 * holds: the run read carries identities, and an administrator needs the name to
 * know which Órgano failed. An identity the catalogue cannot place — imported
 * away between the run and this read — falls back to the identity itself rather
 * than being dropped from a list of what went wrong.
 */
export function describeRun(run: ImportRun, nameOf: (organoId: string) => string): RunReport {
  const failures = failureLines(run, nameOf);
  const failuresTitle = failures.length > 0 ? copy.run.failedOrganos(failures.length) : null;
  const base = { failures, failuresTitle, timing: timing(run) };

  switch (run.state) {
    case 'IN_PROGRESS':
      return {
        ...base,
        tone: 'progress',
        title: copy.run.inProgressTitle,
        counts: counted(organosCovered(run), copy.run.scopeCount),
        note: copy.trigger.guardHeld,
      };
    case 'SUCCEEDED':
      return {
        ...base,
        tone: 'success',
        title: copy.run.succeededTitle,
        counts: `${counted(organosCovered(run), copy.run.coveredCount)} · ${contractCounts(run)}`,
        note: copy.run.succeededNote,
      };
    case 'PARTIALLY_SUCCEEDED':
      return {
        ...base,
        tone: 'partial',
        title: copy.run.partialTitle,
        counts: `${completedOf(run)} · ${contractCounts(run)}`,
        note: null,
      };
    case 'ABANDONED':
      return {
        ...base,
        tone: 'abandoned',
        title: copy.run.abandonedTitle,
        counts: `${completedOf(run)} · ${contractCounts(run)}`,
        note: copy.run.abandonedNote,
      };
    case 'FAILED':
      return {
        ...base,
        tone: 'failure',
        title: copy.run.failedTitle,
        counts: `${completedOf(run)} · ${contractCounts(run)}`,
        note: copy.run.failedNote,
      };
    // A verdict this build does not know. Reported rather than dropped, because
    // the alternative failure mode is silent — and neutrally rather than red,
    // because a state we cannot read is not a run we know went wrong.
    default:
      return {
        ...base,
        tone: 'unknown',
        title: copy.run.unknownTitle,
        counts: `${completedOf(run)} · ${contractCounts(run)}`,
        note: copy.run.unknownNote,
      };
  }
}

/**
 * A refusal, as the two things it has to say: that nothing was imported, and —
 * when it was a mark that asked — that the mark itself stands. Naming the Órgano
 * is what separates the two: only a mark refusal has one.
 */
export function describeRefusal(kind: ImportRefusalKind, organoName: string | null): RefusalReport {
  if (kind === 'IMPORT_ALREADY_RUNNING') {
    return {
      title: organoName === null ? copy.refusal.noImportTitle : copy.refusal.markKeptTitle,
      message: organoName === null ? copy.refusal.guardTrigger : copy.refusal.guardMark(organoName),
      note: copy.refusal.guardNote,
      retryable: true,
    };
  }
  return {
    title: copy.refusal.noImportTitle,
    message:
      organoName === null
        ? copy.refusal.notEligibleTrigger
        : copy.refusal.notEligibleMark(organoName),
    note: copy.refusal.notEligibleNote,
    retryable: false,
  };
}

/**
 * Which refusal a trigger answered with, or null when the request failed rather
 * than being refused. Keyed on the problem `type`: both refusals are `409`, and
 * one clears itself while the other never does.
 */
export function triggerRefusal(error: unknown): ImportRefusalKind | null {
  if (isProblemType(error, PROBLEM_TYPE.IMPORT_ALREADY_RUNNING)) {
    return 'IMPORT_ALREADY_RUNNING';
  }
  return isProblemType(error, PROBLEM_TYPE.ORGANO_NOT_ELIGIBLE) ? 'ORGANO_NOT_ELIGIBLE' : null;
}

/** What to say when the run read itself fails, which is not an outcome at all. */
export function runReadError(error: unknown): string {
  return isProblemType(error, RUN_NOT_FOUND) ? copy.run.errorNotFound : copy.run.errorGeneric;
}
