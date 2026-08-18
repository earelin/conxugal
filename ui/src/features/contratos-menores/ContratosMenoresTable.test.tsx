import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../../shared/lib/strings';
import { formatAmount, formatPublicationDate } from './contractFormat';
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
      expect(row.getByText(formatPublicationDate(laboratorio.publicationDate))).toBeInTheDocument();
      expect(row.getByText(String(laboratorio.sourceId))).toBeInTheDocument();
      expect(row.getByText(laboratorio.obxecto as string)).toBeInTheDocument();
      expect(row.getByText(laboratorio.awardee.name)).toBeInTheDocument();
      expect(row.getByText(laboratorio.awardee.fiscalId)).toBeInTheDocument();
      expect(row.getByText(formatAmount(laboratorio.amount))).toBeInTheDocument();
      expect(row.getByText(laboratorio.duration as string)).toBeInTheDocument();
      expect(row.getByRole('link')).toHaveAttribute('href', laboratorio.sourceUrl);
    });

    it('draws one row per contract the page carries', async () => {
      renderList();
      await screen.findByRole('table');

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
      renderList([verbose]);
      await screen.findByRole('table');

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
      renderList([verbose]);
      await screen.findByRole('table');

      // The object's cell is the one column with no width pinned and no nowrap,
      // which is what leaves it the room the others do not take.
      const cell = screen.getByText(verbose.obxecto as string).closest('td');
      expect(cell).not.toHaveStyle({ whiteSpace: 'nowrap' });
    });
  });

  describe('the awardee', () => {
    it('is text, with no link and no operador identifier', async () => {
      renderList([laboratorio]);
      await screen.findByRole('table');

      const row = within(rowFor(laboratorio));
      // The operador route does not exist yet, and a link to one that 404s is
      // worse than no link — so the only link on the row is the source's.
      expect(row.queryByRole('link', { name: laboratorio.awardee.name })).not.toBeInTheDocument();
      expect(row.getAllByRole('link')).toHaveLength(1);
    });

    it('names no awarding Órgano, every row belonging to the one already open', async () => {
      renderList();
      await screen.findByRole('table');

      expect(within(contractsTable()).queryByText(ORGANO_NAME)).not.toBeInTheDocument();
      expect(
        within(contractsTable()).queryByText(strings.organo.families.contratosMenores),
      ).not.toBeInTheDocument();
    });
  });

  describe('the amount', () => {
    it('is labelled as including VAT on the column that carries it', async () => {
      renderList();
      await screen.findByRole('table');

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
      renderList();
      await screen.findByRole('table');

      // One statement covers all of them and reads once; the same sentence on
      // fifty rows would be fifty things to read.
      expect(screen.getAllByText(copy.durationCaveat)).toHaveLength(1);
      expect(within(contractsTable()).getByText(copy.durationCaveat)).toBeInTheDocument();
    });
  });

  describe('the source link', () => {
    it('is reachable by a name that says which contract it opens', async () => {
      renderList();
      await screen.findByRole('table');

      // «Fonte» alone names none of them, and the column repeats down the page:
      // the accessible name is the only thing telling one icon from the next.
      const link = screen.getByRole('link', {
        name: copy.sourceLinkLabel(String(radiodiagnostico.sourceId)),
      });
      expect(link).toHaveAttribute('href', radiodiagnostico.sourceUrl);
    });

    it('opens the official source away from the app', async () => {
      renderList([laboratorio]);
      await screen.findByRole('table');

      const link = within(rowFor(laboratorio)).getByRole('link');
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', expect.stringContaining('noreferrer'));
    });
  });

  describe('the two values a row can lack', () => {
    it('shows an absent object and duration as absent rather than as invented text', async () => {
      renderList([bare]);
      await screen.findByRole('table');

      const row = within(rowFor(bare));
      expect(row.getAllByText(copy.notPublished)).toHaveLength(2);
      // Not a zero, not a dash, and nothing borrowed from the neighbouring row.
      expect(row.queryByText('0')).not.toBeInTheDocument();
      expect(row.queryByText('—')).not.toBeInTheDocument();
    });

    it('still states the date, the amount and the awardee on that same row', async () => {
      renderList([bare]);
      await screen.findByRole('table');

      // Those three are never absent — a contract missing any of them is
      // withheld — so a row that reaches a reader always carries all three.
      const row = within(rowFor(bare));
      expect(row.getByText(formatPublicationDate(bare.publicationDate))).toBeInTheDocument();
      expect(row.getByText(formatAmount(bare.amount))).toBeInTheDocument();
      expect(row.getByText(bare.awardee.name)).toBeInTheDocument();
    });
  });

  describe('two absences a reader will notice, both deliberate', () => {
    it('offers no CPV filter and no free-text search over the object', async () => {
      renderList();
      await screen.findByRole('table');

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
