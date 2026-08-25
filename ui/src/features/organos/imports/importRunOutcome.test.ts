import { describe, expect, it } from 'vitest';

import { HttpError, ProblemError } from '../../../shared/lib/httpClient';
import { strings } from '../../../shared/lib/strings';
import type {
  ImportRun,
  ImportRunOrgano,
  ImportRunOrganoState,
  ImportRunState,
} from './contratosMenores';
import { describeRefusal, describeRun, runReadError, triggerRefusal } from './importRunOutcome';

const copy = strings.admin.organos.contratosMenores;

const SERGAS = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa';
const INNOVACION = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb';
const PORTOS = 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee';

const NAMES: Record<string, string> = {
  [SERGAS]: 'Servizo Galego de Saúde',
  [INNOVACION]: 'Axencia Galega de Innovación',
  [PORTOS]: 'Portos de Galicia',
};

function nameOf(organoId: string): string {
  return NAMES[organoId] ?? organoId;
}

/** The shipped case by default: a run asked for one family covers each Órgano once. */
function covered(
  organoId: string,
  state: ImportRunOrganoState,
  failureReason: string | null = null,
  family: ImportRunOrgano['family'] = 'CONTRATOS_MENORES',
): ImportRunOrgano {
  return { organoId, family, state, added: 0, refreshed: 0, failureReason };
}

/** The same Órgano in both families, the shape a run covering both produces. */
function bothFamilies(
  organoId: string,
  contratosMenores: ImportRunOrganoState,
  licitacions: ImportRunOrganoState,
): ImportRunOrgano[] {
  return [covered(organoId, contratosMenores), covered(organoId, licitacions, null, 'LICITACIONS')];
}

function run(state: ImportRunState, overrides: Partial<ImportRun> = {}): ImportRun {
  return {
    id: 'r-1',
    importer: 'CONTRATOS_MENORES',
    state,
    startedAt: '2026-08-13T06:14:02Z',
    finishedAt: '2026-08-13T09:42:00Z',
    added: 18402,
    refreshed: 96,
    coveredOrganos: [covered(SERGAS, 'SUCCEEDED'), covered(INNOVACION, 'SUCCEEDED')],
    ...overrides,
  };
}

describe('describeRun', () => {
  it('gives every verdict its own title and colour, so none can be mistaken for another', () => {
    const states: ImportRunState[] = [
      'IN_PROGRESS',
      'SUCCEEDED',
      'PARTIALLY_SUCCEEDED',
      'FAILED',
      'ABANDONED',
    ];

    const reports = states.map((state) => describeRun(run(state), nameOf));

    expect(new Set(reports.map((report) => report.title)).size).toBe(states.length);
    expect(new Set(reports.map((report) => report.tone)).size).toBe(states.length);
  });

  it('reddens only the run in which nothing could be imported', () => {
    expect(describeRun(run('FAILED'), nameOf).tone).toBe('failure');
    expect(describeRun(run('PARTIALLY_SUCCEEDED'), nameOf).tone).not.toBe('failure');
    expect(describeRun(run('ABANDONED'), nameOf).tone).not.toBe('failure');
  });

  it('counts a finished run in Órganos and contracts, grouped for reading', () => {
    const report = describeRun(run('SUCCEEDED'), nameOf);

    expect(report.counts).toBe(
      `2 ${copy.run.coveredCount.plural} · 18 402 ${copy.run.added.plural} · 96 ${copy.run.refreshed.plural}`,
    );
  });

  it('says how many of the scope a partial run completed, not just how many it covered', () => {
    const partial = run('PARTIALLY_SUCCEEDED', {
      coveredOrganos: [
        covered(SERGAS, 'SUCCEEDED'),
        covered(INNOVACION, 'FAILED', 'a fonte non respondeu'),
      ],
    });

    expect(describeRun(partial, nameOf).counts).toContain(copy.run.completedOf(1, 2));
  });

  it('names the Órgano that failed and what it failed on', () => {
    const partial = run('PARTIALLY_SUCCEEDED', {
      coveredOrganos: [
        covered(SERGAS, 'SUCCEEDED'),
        covered(INNOVACION, 'FAILED', 'a fonte non respondeu'),
      ],
    });

    const report = describeRun(partial, nameOf);

    expect(report.failuresTitle).toBe(copy.run.failedOrganos(1));
    // The identity travels with the line: two Órganos can share a name, and two
    // failures the same reason, so the line alone is not a key.
    expect(report.failures).toEqual([
      { organoId: INNOVACION, line: 'Axencia Galega de Innovación · a fonte non respondeu' },
    ]);
  });

  it('keeps two identically-named failures apart by the identity behind them', () => {
    const twin = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd';
    const partial = run('PARTIALLY_SUCCEEDED', {
      coveredOrganos: [
        covered(INNOVACION, 'FAILED', 'a fonte non respondeu'),
        covered(twin, 'FAILED', 'a fonte non respondeu'),
      ],
    });

    const identities = describeRun(partial, () => 'Axencia Galega de Innovación').failures.map(
      (failure) => failure.organoId,
    );

    expect(identities).toEqual([INNOVACION, twin]);
  });

  it('falls back to the identity of an Órgano the catalogue can no longer place', () => {
    const partial = run('PARTIALLY_SUCCEEDED', {
      coveredOrganos: [covered('cccccccc-cccc-7ccc-8ccc-cccccccccccc', 'FAILED')],
    });

    expect(describeRun(partial, nameOf).failures).toEqual([
      {
        organoId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        line: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      },
    ]);
  });

  it('counts the Órganos a two-family run covered, not the rows it holds for them', () => {
    const both = run('SUCCEEDED', {
      importer: 'AMBAS_FAMILIAS',
      coveredOrganos: [
        ...bothFamilies(SERGAS, 'SUCCEEDED', 'SUCCEEDED'),
        ...bothFamilies(INNOVACION, 'SUCCEEDED', 'SUCCEEDED'),
        ...bothFamilies(PORTOS, 'SUCCEEDED', 'SUCCEEDED'),
      ],
    });

    // Three Órganos, six rows. Six would be plausible enough to survive a glance.
    expect(describeRun(both, nameOf).counts).toContain(`3 ${copy.run.coveredCount.plural}`);
  });

  it('counts an Órgano that failed in one of its two families as not completed', () => {
    const mixed = run('PARTIALLY_SUCCEEDED', {
      importer: 'AMBAS_FAMILIAS',
      coveredOrganos: bothFamilies(SERGAS, 'SUCCEEDED', 'FAILED'),
    });

    expect(describeRun(mixed, nameOf).counts).toContain(copy.run.completedOf(0, 1));
  });

  it('lists an Órgano that failed in both families once, carrying both reasons', () => {
    const failed = run('FAILED', {
      importer: 'AMBAS_FAMILIAS',
      coveredOrganos: [
        covered(SERGAS, 'FAILED', 'a fonte non respondeu'),
        covered(SERGAS, 'FAILED', 'o rexistro non se puido ler', 'LICITACIONS'),
      ],
    });

    const report = describeRun(failed, nameOf);

    // One Órgano failed, so the title above the list has to say one.
    expect(report.failuresTitle).toBe(copy.run.failedOrganos(1));
    expect(report.failures).toEqual([
      {
        organoId: SERGAS,
        line: 'Servizo Galego de Saúde · a fonte non respondeu · o rexistro non se puido ler',
      },
    ]);
  });

  it("says once what both of an Órgano's families failed on, when they failed alike", () => {
    // The likely shape rather than the interesting one: both families read the
    // same source through the same failure mapping, so one outage gives them
    // the identical reason — and the line names no family to tell them apart.
    const failed = run('FAILED', {
      importer: 'AMBAS_FAMILIAS',
      coveredOrganos: [
        covered(SERGAS, 'FAILED', 'a fonte non respondeu'),
        covered(SERGAS, 'FAILED', 'a fonte non respondeu', 'LICITACIONS'),
      ],
    });

    expect(describeRun(failed, nameOf).failures).toEqual([
      { organoId: SERGAS, line: 'Servizo Galego de Saúde · a fonte non respondeu' },
    ]);
  });

  it('reads a run triggered for both families by its verdict, not by what it imported', () => {
    const both = run('SUCCEEDED', { importer: 'AMBAS_FAMILIAS' });

    expect(describeRun(both, nameOf).title).toBe(copy.run.succeededTitle);
    expect(describeRun(both, nameOf).tone).toBe('success');
  });

  it('reports a verdict it does not know instead of dressing it as a failure', () => {
    const report = describeRun({ ...run('SUCCEEDED'), state: 'RESUMING' as never }, nameOf);

    expect(report.title).toBe(copy.run.unknownTitle);
    expect(report.tone).not.toBe('failure');
  });

  it('lists neither a stopped nor a skipped Órgano as a failure', () => {
    const finished = run('SUCCEEDED', {
      coveredOrganos: [covered(SERGAS, 'STOPPED', 'unmarked'), covered(INNOVACION, 'SKIPPED')],
    });

    const report = describeRun(finished, nameOf);

    expect(report.failures).toEqual([]);
    expect(report.failuresTitle).toBeNull();
  });

  it('reports a run still going by when it started, never by when it ended', () => {
    const report = describeRun(run('IN_PROGRESS', { finishedAt: null }), nameOf);

    expect(report.timing).toContain(copy.run.startedAtPrefix);
    expect(report.timing).not.toContain(copy.run.finishedAtPrefix);
  });

  it('says an abandoned run never ended rather than inventing a finish time', () => {
    const report = describeRun(run('ABANDONED', { finishedAt: null }), nameOf);

    expect(report.timing).toContain(copy.run.unfinished);
  });
});

describe('describeRefusal', () => {
  it('says the mark was kept and only the import refused, naming the Órgano', () => {
    const report = describeRefusal('IMPORT_ALREADY_RUNNING', 'Servizo Galego de Saúde');

    expect(report.title).toBe(copy.refusal.markKeptTitle);
    expect(report.message).toBe(copy.refusal.guardMark('Servizo Galego de Saúde'));
    // The guard clears on its own, so asking again is worth offering.
    expect(report.retryable).toBe(true);
  });

  it('does not claim a mark was kept when it was a section-wide trigger that was refused', () => {
    const report = describeRefusal('IMPORT_ALREADY_RUNNING', null);

    expect(report.title).toBe(copy.refusal.noImportTitle);
    expect(report.message).toBe(copy.refusal.guardTrigger);
  });

  it('tells an ineligible Órgano apart from a held guard, and offers no retry for it', () => {
    const guard = describeRefusal('IMPORT_ALREADY_RUNNING', 'Hospital Álvaro Cunqueiro');
    const ineligible = describeRefusal('ORGANO_NOT_ELIGIBLE', 'Hospital Álvaro Cunqueiro');

    expect(ineligible.message).not.toBe(guard.message);
    expect(ineligible.note).not.toBe(guard.note);
    // Nothing about asking again changes the answer until the catalogue does.
    expect(ineligible.retryable).toBe(false);
  });
});

describe('triggerRefusal', () => {
  function refused(type: string): ProblemError {
    return new ProblemError(409, `urn:conxugal:problem-type:${type}`, null, 'Conflict');
  }

  it('tells the two refusals apart by type, which is all that separates two 409s', () => {
    expect(triggerRefusal(refused('import-already-running'))).toBe('IMPORT_ALREADY_RUNNING');
    expect(triggerRefusal(refused('organo-not-eligible'))).toBe('ORGANO_NOT_ELIGIBLE');
  });

  it('refuses to read a bare 409 as either, because the status alone says nothing', () => {
    expect(triggerRefusal(new HttpError(409, 'Conflict'))).toBeNull();
  });

  it('leaves a genuine failure to be reported as one', () => {
    expect(triggerRefusal(new HttpError(500, 'Server Error'))).toBeNull();
  });
});

describe('runReadError', () => {
  it('says a run is gone rather than blaming the network', () => {
    const gone = new ProblemError(
      404,
      'urn:conxugal:problem-type:import-run-not-found',
      null,
      'Not Found',
    );

    expect(runReadError(gone)).toBe(copy.run.errorNotFound);
    expect(runReadError(new HttpError(500, 'Server Error'))).toBe(copy.run.errorGeneric);
  });
});
