import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { formatCalendarDate } from '../../shared/lib/date';
import { formatEuros } from '../../shared/lib/number';
import { strings } from '../../shared/lib/strings';
import type { ContratoMenor } from './contracts';
import {
  contract,
  contractsTable,
  copy,
  mockContracts,
  ORGANO_NAME,
  page,
  renderSection,
  rowFor,
  SECTION_PATH,
  summary,
  yearChooser,
} from './sectionHarness';

const laboratorio = contract();

const radiodiagnostico = contract({
  sourceId: 1179004,
  publicationDate: '2025-02-28',
  obxecto: 'Servizo de mantemento de equipos de radiodiagnóstico',
  amount: 8750.5,
  duration: '6 meses',
  awardee: { name: 'TECNOMÉDICA NOROESTE, S.A.', fiscalId: 'ESA36112233' },
  sourceUrl: 'https://www.contratosdegalicia.gal/licitacion?N=1179004',
});

/** Both of the two values a row can lack, on one contract. */
const bare = contract({ sourceId: 1160245, obxecto: null, duration: null });

function renderList(items = [laboratorio, radiodiagnostico], year = 2025) {
  mockContracts(year, 200, page(items));
  return renderSection(summary());
}

/**
 * The list with its read already answered, which is where all but the loading
 * and failure cases below start. Awaiting the table is what separates them from
 * the wait that precedes it — asserting before it arrives would be asserting
 * against the loading state.
 */
async function showList(items?: ContratoMenor[]) {
  renderList(items);
  await screen.findByRole('table');
}

describe('the contract row', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  describe('what a row carries', () => {
    it('states every attribute the system holds, there being no detail view to open', async () => {
      renderList();

      const row = within(await screen.findByRole('row', { name: /1234567/ }));
      expect(row.getByText(formatCalendarDate(laboratorio.publicationDate))).toBeInTheDocument();
      expect(row.getByText(String(laboratorio.sourceId))).toBeInTheDocument();
      expect(row.getByText(laboratorio.obxecto as string)).toBeInTheDocument();
      expect(row.getByText(laboratorio.awardee.name)).toBeInTheDocument();
      expect(row.getByText(laboratorio.awardee.fiscalId)).toBeInTheDocument();
      expect(row.getByText(formatEuros(laboratorio.amount))).toBeInTheDocument();
      expect(row.getByText(laboratorio.duration as string)).toBeInTheDocument();
      expect(row.getByRole('link')).toHaveAttribute('href', laboratorio.sourceUrl);
    });

    it('draws one row per contract the page carries', async () => {
      await showList();

      // The header row plus the two contracts, and nothing invented between
      // them: there is no empty state and no filler row.
      expect(within(contractsTable()).getAllByRole('row')).toHaveLength(3);
    });

    it('renders the published text character for character', async () => {
      const verbose = contract({
        sourceId: 42,
        obxecto:
          'SUBMINISTRACIÓN de material funxible de laboratorio para o Hospital de Santiago, ' +
          'incluíndo o seu transporte, a súa instalación e a formación do persoal que o emprega',
        duration: '12  meses',
        awardee: { name: 'lingua atlántica, S. COOP. Galega', fiscalId: 'ESF15667788' },
      });
      await showList([verbose]);

      // Not truncated, not case-folded, not respaced. The default matcher
      // collapses runs of whitespace before comparing, which would let a row
      // that tidied the duration's double space pass — so these compare the
      // text node as it actually stands.
      const verbatim = { exact: true, normalizer: (text: string) => text };
      const row = within(rowFor(verbose));
      expect(row.getByText(verbose.obxecto as string, verbatim)).toBeInTheDocument();
      expect(row.getByText(verbose.awardee.name, verbatim)).toBeInTheDocument();
      expect(row.getByText(verbose.duration as string, verbatim)).toBeInTheDocument();
    });

    it('lets a long object wrap rather than clipping it', async () => {
      const verbose = contract({
        sourceId: 42,
        obxecto: 'Reparación urxente da instalación de climatización do bloque cirúrxico',
      });
      await showList([verbose]);

      // Asserted as the pairing it is, because the object's cell carries no
      // style at all: on its own, "no nowrap here" would pass for an
      // implementation that pinned no column anywhere.
      const cell = screen.getByText(verbose.obxecto as string).closest('td');
      const dateCell = screen.getByText(String(verbose.sourceId)).closest('td');
      expect(dateCell).toHaveStyle({ whiteSpace: 'nowrap' });
      expect(cell).not.toHaveStyle({ whiteSpace: 'nowrap' });
      // And nothing clips what it wraps, which is the other way the column
      // could swallow a long object while still passing the check above.
      expect(cell).not.toHaveStyle({ overflow: 'hidden' });
      expect(cell).not.toHaveStyle({ textOverflow: 'ellipsis' });
    });

    it('leaves the two unbounded text columns free to wrap', async () => {
      // The awardee's name has no length cap on the wire and the duration is
      // capped at 64 characters the source does spend. Held on one line, either
      // would widen the table by hundreds of pixels and squeeze the object down
      // to a word per line — the opposite of what the widths are for.
      const wordy = contract({
        sourceId: 77,
        duration: 'Desde a formalización do contrato ata o 31 de decembro de 2025',
        awardee: {
          name: 'SUBMINISTRACIÓNS HOSPITALARIAS DO NOROESTE PENINSULAR, S.L.U.',
          fiscalId: 'ESB15234567',
        },
      });
      await showList([wordy]);

      expect(screen.getByText(wordy.awardee.name).closest('td')).not.toHaveStyle({
        whiteSpace: 'nowrap',
      });
      expect(screen.getByText(wordy.duration as string).closest('td')).not.toHaveStyle({
        whiteSpace: 'nowrap',
      });
    });
  });

  describe('the awardee', () => {
    it('is text, with no link and no operador identifier', async () => {
      await showList([laboratorio]);

      const row = within(rowFor(laboratorio));
      // The operador route does not exist yet, and a link to one that 404s is
      // worse than no link — so the only link on the row is the source's.
      expect(row.queryByRole('link', { name: laboratorio.awardee.name })).not.toBeInTheDocument();
      expect(row.getAllByRole('link')).toHaveLength(1);
    });

    it('names no awarding Órgano, every row belonging to the one already open', async () => {
      await showList();

      expect(within(contractsTable()).queryByText(ORGANO_NAME)).not.toBeInTheDocument();
      expect(
        within(contractsTable()).queryByText(strings.organo.families.contratosMenores),
      ).not.toBeInTheDocument();
    });
  });

  describe('the amount', () => {
    it('is labelled as including VAT on the column that carries it', async () => {
      await showList();

      // The thresholds that define a contrato menor are VAT-exclusive, so an
      // unlabelled figure invites exactly the wrong comparison.
      const header = screen.getByRole('columnheader', {
        name: new RegExp(copy.columnAmountVat),
      });
      expect(header).toHaveTextContent(copy.columnAmount);
    });
  });

  describe('the duration', () => {
    it('carries the caveat once, on the column rather than on every row', async () => {
      await showList();

      // One statement covers all of them and reads once; the same sentence on
      // fifty rows would be fifty things to read.
      expect(screen.getAllByText(copy.durationCaveat)).toHaveLength(1);
    });

    it('names the caveat from the column header it qualifies', async () => {
      await showList();

      // The `ⓘ` is decorative, so this is the only thing that carries the
      // caveat to a reader who never sees it. Announced with the column rather
      // than met after the last row, fifty of them away from what it is about.
      const header = screen.getByRole('columnheader', { name: copy.columnDuration });
      expect(header).toHaveAccessibleDescription(copy.durationCaveat);
    });

    it('keeps the caveat out of the table’s own sideways scroll', async () => {
      await showList();

      // Inside it, the sentence was held to the table's 720 px and had to be
      // read by scrolling right and back again for each line at 360 px. A table
      // may scroll sideways; a sentence may not.
      expect(within(contractsTable()).queryByText(copy.durationCaveat)).not.toBeInTheDocument();
      expect(screen.getByText(copy.durationCaveat)).toBeInTheDocument();
    });
  });

  describe('the sideways scroll six columns need', () => {
    it('is a region a keyboard can reach and scroll back', async () => {
      await showList();

      // Without this the only focusable things in the table are the source
      // links in the last column: tabbing in arrives scrolled fully right, with
      // nothing further left to bring it back.
      const region = screen.getByRole('region', { name: copy.tableLabel });
      expect(region).toHaveAttribute('tabindex', '0');
      expect(region).toContainElement(contractsTable());
    });
  });

  describe('the source link', () => {
    it('is reachable by a name that says which contract it opens', async () => {
      await showList();

      // «Fonte» alone names none of them, and the column repeats down the page:
      // the accessible name is the only thing telling one icon from the next.
      const link = screen.getByRole('link', {
        name: copy.sourceLinkLabel(String(radiodiagnostico.sourceId)),
      });
      expect(link).toHaveAttribute('href', radiodiagnostico.sourceUrl);
    });

    it('opens the official source away from the app', async () => {
      await showList([laboratorio]);

      const link = within(rowFor(laboratorio)).getByRole('link');
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', expect.stringContaining('noreferrer'));
    });

    it('is a target a finger can hit rather than the glyph’s own 18 px', async () => {
      await showList([laboratorio]);

      // It sits beside the gesture that scrolls this table sideways, so a
      // target the size of the icon is one a reader misses into a scroll.
      const link = within(rowFor(laboratorio)).getByRole('link');
      expect(link).toHaveClass('mantine-ActionIcon-root');
    });
  });

  describe('the two values a row can lack', () => {
    it('shows an absent object and duration as absent rather than as invented text', async () => {
      await showList([bare]);

      const row = within(rowFor(bare));
      expect(row.getAllByText(copy.notPublished)).toHaveLength(2);
      // Not a zero, not a dash, and nothing borrowed from the neighbouring row.
      expect(row.queryByText('0')).not.toBeInTheDocument();
      expect(row.queryByText('—')).not.toBeInTheDocument();
    });

    it('still states the date, the amount and the awardee on that same row', async () => {
      await showList([bare]);

      // Those three are never absent — a contract missing any of them is
      // withheld — so a row that reaches a reader always carries all three.
      const row = within(rowFor(bare));
      expect(row.getByText(formatCalendarDate(bare.publicationDate))).toBeInTheDocument();
      expect(row.getByText(formatEuros(bare.amount))).toBeInTheDocument();
      expect(row.getByText(bare.awardee.name)).toBeInTheDocument();
    });
  });

  describe('two absences a reader will notice, both deliberate', () => {
    it('offers no CPV filter and no free-text search over the object', async () => {
      await showList();

      // Not hidden and not disabled: there is simply no control. The year
      // chooser is the only one the section has.
      expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
      expect(screen.getAllByRole('combobox')).toEqual([yearChooser()]);
    });
  });

  describe('while the read is in flight or has failed', () => {
    it('says it is loading, with the section’s own statements still on screen', () => {
      mockContracts(2025, 200, page([laboratorio]));
      renderSection(summary({ partial: true, updating: false }));

      expect(screen.getByText(strings.loading)).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getByText(copy.partialBody)).toBeInTheDocument();
      expect(screen.getByText(copy.notUpdatedBody)).toBeInTheDocument();
    });

    it('reports a failure with a retry, blanking neither statement nor chooser', async () => {
      mockContracts(2025, 500);
      renderSection(summary({ partial: true, updating: false }));

      expect(await screen.findByText(copy.errorTitle)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: strings.retry })).toBeInTheDocument();
      expect(screen.getByText(copy.partialBody)).toBeInTheDocument();
      expect(screen.getByText(copy.notUpdatedBody)).toBeInTheDocument();
      expect(yearChooser()).toBeInTheDocument();
    });

    it('replaces the failure with the table when the retry succeeds', async () => {
      const user = userEvent.setup();
      mockContracts(2025, 500);
      mockContracts(2025, 200, page([laboratorio]));
      renderSection(summary());
      await screen.findByText(copy.errorTitle);

      await user.click(screen.getByRole('button', { name: strings.retry }));

      expect(await screen.findByRole('table')).toBeInTheDocument();
      expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
      expect(nock.pendingMocks()).toEqual([]);
    });
  });

  describe('the selection the read is keyed on', () => {
    it('asks the server again for the year a reader chooses', async () => {
      const user = userEvent.setup();
      mockContracts(2025, 200, page([laboratorio]));
      mockContracts(2023, 200, page([radiodiagnostico]));
      renderSection(summary());
      await screen.findByText(laboratorio.awardee.name);

      await user.click(yearChooser());
      await user.click(screen.getByRole('option', { name: '2023' }));

      // A different year is a different query, not a mutation of the answered
      // one — proved by the second interceptor being the one that answers.
      expect(await screen.findByText(radiodiagnostico.awardee.name)).toBeInTheDocument();
      expect(screen.queryByText(laboratorio.awardee.name)).not.toBeInTheDocument();
      expect(nock.pendingMocks()).toEqual([]);
    });

    it('reads the year the URL already names rather than the default', async () => {
      mockContracts(2024, 200, page([radiodiagnostico]));

      renderSection(summary(), `${SECTION_PATH}?year=2024`);

      expect(await screen.findByText(radiodiagnostico.awardee.name)).toBeInTheDocument();
      await waitFor(() => {
        expect(nock.pendingMocks()).toEqual([]);
      });
    });
  });
});
