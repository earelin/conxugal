import { formatHourMinute } from '../../shared/lib/date';
import { isProblemType } from '../../shared/lib/httpError';
import { formatCount } from '../../shared/lib/number';
import { singularOrPlural, type Word } from '../../shared/lib/plural';
import { strings } from '../../shared/lib/strings';
import type { ImportRefusalKind, ImportRun } from './contratosMenores';

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
export type RunTone = 'progress' | 'success' | 'partial' | 'failure' | 'abandoned';

export interface RunReport {
  tone: RunTone;
  title: string;
  /** Órganos and contracts, never shown for a refusal — there are none. */
  counts: string;
  failuresTitle: string | null;
  failures: string[];
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

function counted(count: number, word: Word): string {
  return `${formatCount(count)} ${singularOrPlural(count, word)}`;
}

function at(iso: string): string {
  return formatHourMinute(new Date(iso));
}

function contractCounts(run: ImportRun): string {
  return `${counted(run.added, copy.run.added)} · ${counted(run.refreshed, copy.run.refreshed)}`;
}

function completedOf(run: ImportRun): string {
  const completed = run.coveredOrganos.filter((organo) => organo.state === 'SUCCEEDED').length;
  return copy.run.completedOf(completed, run.coveredOrganos.length);
}

function failureLines(run: ImportRun, nameOf: (organoId: string) => string): string[] {
  return run.coveredOrganos
    .filter((organo) => organo.state === 'FAILED')
    .map((organo) => {
      const name = nameOf(organo.organoId);
      return organo.failureReason === null ? name : `${name} · ${organo.failureReason}`;
    });
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
        counts: counted(run.coveredOrganos.length, copy.run.scopeCount),
        note: copy.trigger.guardHeld,
      };
    case 'SUCCEEDED':
      return {
        ...base,
        tone: 'success',
        title: copy.run.succeededTitle,
        counts: `${counted(run.coveredOrganos.length, copy.run.coveredCount)} · ${contractCounts(run)}`,
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
    default:
      return {
        ...base,
        tone: 'failure',
        title: copy.run.failedTitle,
        counts: `${completedOf(run)} · ${contractCounts(run)}`,
        note: copy.run.failedNote,
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
