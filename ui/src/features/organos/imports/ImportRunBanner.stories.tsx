import type { Meta, StoryObj } from '@storybook/react-vite';
import type { UseQueryResult } from '@tanstack/react-query';
import { fn } from 'storybook/test';

import { ProblemError } from '../../../shared/lib/httpClient';
import { cunqueiro, innovacion, ORGANOS, sergas } from '../storyFixtures';
import type { ImportRun, ImportRunState } from './contratosMenores';
import { ImportRunBanner } from './ImportRunBanner';

const STARTED_AT = '2026-08-23T07:15:00Z';
const FINISHED_AT = '2026-08-23T09:02:00Z';
// Relative, not a fixed instant: `CheckedAgo` renders how long ago the run was
// read, so a pinned date would drift into "Consultado hai 720 h" within a month
// and the just-now branch would never be seen again.
const READ_AT = Date.now() - 4 * 60_000;

function run(state: ImportRunState, overrides: Partial<ImportRun> = {}): ImportRun {
  return {
    id: 'run-1',
    importer: 'CONTRATOS_MENORES',
    state,
    startedAt: STARTED_AT,
    finishedAt: state === 'IN_PROGRESS' || state === 'ABANDONED' ? null : FINISHED_AT,
    added: 1_284,
    refreshed: 96,
    coveredOrganos: [
      {
        organoId: sergas.id,
        family: 'CONTRATOS_MENORES',
        state: 'SUCCEEDED',
        added: 900,
        refreshed: 60,
        failureReason: null,
      },
      {
        organoId: innovacion.id,
        family: 'CONTRATOS_MENORES',
        state: 'SUCCEEDED',
        added: 384,
        refreshed: 36,
        failureReason: null,
      },
    ],
    ...overrides,
  };
}

/**
 * The banner takes the run read whole rather than a plain value, because the
 * toolbar beside it owns that read — whether an import is in progress decides
 * whether either trigger may be pressed, so the two cannot read it separately.
 *
 * Only the handful of fields the banner touches are stood up here; the cast is
 * what keeps these stories from having to fake the rest of react-query's
 * result object.
 */
function settled(data: ImportRun): UseQueryResult<ImportRun> {
  return {
    data,
    isError: false,
    isFetching: false,
    error: null,
    dataUpdatedAt: READ_AT,
  } as UseQueryResult<ImportRun>;
}

function failedRead(error: unknown, isFetching = false): UseQueryResult<ImportRun> {
  return {
    data: undefined,
    isError: true,
    isFetching,
    error,
    dataUpdatedAt: 0,
  } as UseQueryResult<ImportRun>;
}

/**
 * The outcome of the one import this session asked for. Nothing here is a
 * progress indicator — no percentage, no *n de m*, no bar: *Actualizar* is the
 * only thing that re-reads, and watching a live run belongs to a later spec.
 */
const meta = {
  component: ImportRunBanner,
  tags: ['autodocs'],
  args: {
    attempt: { kind: 'run', runId: 'run-1' },
    run: settled(run('SUCCEEDED')),
    catalogue: ORGANOS,
    onRetry: fn(),
    retrying: false,
    onDismiss: fn(),
    onRefresh: fn(),
  },
} satisfies Meta<typeof ImportRunBanner>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Succeeded: Story = {};

export const InProgress: Story = {
  args: { run: settled(run('IN_PROGRESS')) },
};

/** Some Órganos failed, so the banner names them from the catalogue. */
export const PartiallySucceeded: Story = {
  args: {
    run: settled(
      run('PARTIALLY_SUCCEEDED', {
        added: 900,
        refreshed: 60,
        coveredOrganos: [
          {
            organoId: sergas.id,
            family: 'CONTRATOS_MENORES',
            state: 'SUCCEEDED',
            added: 900,
            refreshed: 60,
            failureReason: null,
          },
          {
            organoId: cunqueiro.id,
            family: 'CONTRATOS_MENORES',
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'Upstream timeout',
          },
        ],
      }),
    ),
  },
};

/**
 * A run covering each Órgano in both families, and so holding two rows per
 * Órgano. Every figure here counts Órganos rather than rows: two covered, not
 * four — and the one that failed in both families is listed once, carrying both
 * reasons.
 */
export const BothFamilies: Story = {
  args: {
    run: settled(
      run('PARTIALLY_SUCCEEDED', {
        importer: 'AMBAS_FAMILIAS',
        added: 900,
        refreshed: 60,
        coveredOrganos: [
          {
            organoId: sergas.id,
            family: 'CONTRATOS_MENORES',
            state: 'SUCCEEDED',
            added: 900,
            refreshed: 60,
            failureReason: null,
          },
          {
            organoId: sergas.id,
            family: 'LICITACIONS',
            state: 'SUCCEEDED',
            added: 0,
            refreshed: 0,
            failureReason: null,
          },
          {
            organoId: cunqueiro.id,
            family: 'CONTRATOS_MENORES',
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'Upstream timeout',
          },
          {
            organoId: cunqueiro.id,
            family: 'LICITACIONS',
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'The record could not be read',
          },
        ],
      }),
    ),
  },
};

/** The one red state: a run in which no Órgano could be imported. */
export const Failed: Story = {
  args: {
    run: settled(
      run('FAILED', {
        added: 0,
        refreshed: 0,
        coveredOrganos: [
          {
            organoId: sergas.id,
            family: 'CONTRATOS_MENORES',
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'Upstream unavailable',
          },
        ],
      }),
    ),
  },
};

/** A run whose process died and so never wrote its own ending. */
export const Abandoned: Story = {
  args: { run: settled(run('ABANDONED')) },
};

/**
 * A refusal is not an error: nothing broke, and this one even wrote the mark it
 * was asked for. Grey, and carrying no counts.
 */
export const RefusedButMarked: Story = {
  args: {
    attempt: {
      kind: 'refusal',
      refusal: 'IMPORT_ALREADY_RUNNING',
      organo: cunqueiro,
      refusedAt: new Date(READ_AT),
    },
  },
};

/** A section-wide trigger refused, which names no Órgano. */
export const TriggerRefused: Story = {
  args: {
    attempt: {
      kind: 'refusal',
      refusal: 'IMPORT_ALREADY_RUNNING',
      organo: null,
      refusedAt: new Date(READ_AT),
    },
  },
};

/**
 * The run identity no longer resolves. Dismissible like every other report
 * here — leaving an alert nothing can clear on screen is what a 404 on a run
 * would otherwise do for good.
 */
export const RunNotFound: Story = {
  args: {
    // A `ProblemError` carrying the type, not a bare 404: `runReadError` keys on
    // the problem type and never on the status, so an `HttpError` here would
    // quietly render the generic message this story exists to be distinct from.
    run: failedRead(
      new ProblemError(404, 'urn:conxugal:problem-type:import-run-not-found', null, 'Not Found'),
    ),
  },
};

/** Anything the build cannot name a rule for falls back to the generic message. */
export const ReReadingAfterFailure: Story = {
  args: { run: failedRead(new ProblemError(503, 'about:blank', null, 'Unavailable'), true) },
};

/** Nothing was asked for this session, so the banner renders nothing at all. */
export const NoAttempt: Story = {
  args: { attempt: null },
};
