import { screen, within } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../shared/lib/strings';
import { BASE_URL, mockCurrentUser, mockOrganosPicker, renderApp } from '../test/renderApp';

const pageCopy = strings.organo;
const sectionCopy = strings.contratosMenores;

const ORGANO_ID = 'o-1';
const ORGANO_NAME = 'Servizo Galego de Saúde';
const FAMILY_PATH = 'contratos-menores';
/**
 * The two addresses under test: the page the picker sends a reader to, and the
 * family route it redirects on to. Every case enters at one or the other.
 */
const PAGE_PATH = `/organo/${ORGANO_ID}`;
const SECTION_PATH = `${PAGE_PATH}/${FAMILY_PATH}`;

/**
 * The fixtures are declared here rather than taken from the page slice's
 * harness: the app layer may reach a feature only through its barrel, so a test
 * sitting beside the router cannot borrow one slice's internals to assert about
 * another's. They mirror that harness's shape, so the two read alike where they
 * must be changed together.
 */
const HOLDS_CONTRATOS_MENORES = {
  contratosMenores: {
    route: FAMILY_PATH,
    summary: { years: [2025, 2024, 2023], partial: false, updating: true },
  },
};

function member(families: Record<string, unknown>) {
  return { id: ORGANO_ID, name: ORGANO_NAME, families };
}

function mockOrgano(status: number, body?: object) {
  return nock(BASE_URL).get(`/api/organo/${ORGANO_ID}`).reply(status, body);
}

/**
 * The section's own read of the contract list, which belongs to FEAT-0011 and
 * not to this composition. It is stubbed rather than left to fail because the
 * section is mounted for real here: an unstubbed read is a network error rather
 * than an `HttpError`, so the client retries it behind every assertion and
 * leaves the panel showing an error none of these cases is about.
 *
 * Matched on the year as well as the path, so a case only passes if the section
 * asked for the year the case is about.
 */
function mockContracts(year: number) {
  return nock(BASE_URL)
    .get(`/api/organo/${ORGANO_ID}/${FAMILY_PATH}`)
    .query({ year: String(year) })
    .reply(200, { items: [], page: 1, size: 50, totalItems: 0, totalPages: 0 });
}

/**
 * The page and the section are separate chunks, so every entry into this screen
 * settles in two steps: awaiting the chooser is awaiting the second one.
 */
function yearChooser() {
  return screen.findByRole('combobox', { name: sectionCopy.yearLabel });
}

/**
 * The route tree's own assertion: that the page and the section, which cannot
 * import each other, are composed into one screen here and nowhere else. Each
 * slice's suite proves what it renders; this one proves the section renders
 * *inside* the page, and takes its summary out of the page's single read.
 */
describe('the contratos menores section, mounted by the application router', () => {
  beforeEach(() => {
    nock.disableNetConnect();
    mockCurrentUser('USER');
    mockOrganosPicker();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('answers the bare path with the page and the section it redirects to', async () => {
    mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2025);

    const { router } = renderApp(PAGE_PATH);

    const chooser = await yearChooser();
    expect(chooser).toHaveValue('2025');
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(SECTION_PATH);

    // Inside the page, not beside it: the tab bar is the page's, the chooser is
    // the section's, and the section sits in the panel the active tab owns.
    // Both on screen at once would also be true of a section that had replaced
    // the frame, which is exactly what this route used to do.
    const bar = screen.getByRole('tablist', { name: pageCopy.tabsLabel });
    const tab = within(bar).getByRole('tab', { name: pageCopy.families.contratosMenores });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toContainElement(chooser);
  });

  it('renders the same page for a link straight to the family, without redirecting', async () => {
    mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2025);

    const { router } = renderApp(SECTION_PATH);

    expect(await yearChooser()).toHaveValue('2025');
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(SECTION_PATH);
    // Arrived rather than been sent: nothing replaced the entry it opened on.
    expect(router.state.historyAction).toBe('POP');
  });

  it('opens the chooser on the year a deep link names', async () => {
    mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2023);

    renderApp(`${SECTION_PATH}?year=2023`);

    expect(await yearChooser()).toHaveValue('2023');
  });

  it("carries the section's own query string through the page's redirect", async () => {
    mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2024);

    const { router } = renderApp(`${PAGE_PATH}?year=2024&sort=amount%2Cdesc&page=3`);

    expect(await yearChooser()).toHaveValue('2024');
    expect(router.state.location.pathname).toBe(SECTION_PATH);
    // The family is the path and the selection is the query string, so the two
    // travel together: the page owns none of these three and drops none of them.
    const asked = new URLSearchParams(router.state.location.search);
    expect(asked.get('year')).toBe('2024');
    expect(asked.get('sort')).toBe('amount,desc');
    expect(asked.get('page')).toBe('3');
  });

  it('gives the section its summary as context, asking the server for it once', async () => {
    const scope = mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2025);

    const { queryClient } = renderApp(PAGE_PATH);

    await yearChooser();

    // Every read this screen makes, named in full rather than counted. The
    // filter is on the serialised key rather than its head because a summary
    // read added later would name the Órgano whatever it called itself, and
    // react-query's idiomatic shape puts it in an options object —
    // `['contratosMenores', { organoId }]` — that a check on the head would miss.
    const scopedToThisOrgano = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
      .filter((key) => JSON.stringify(key).includes(ORGANO_ID));
    // The member read is the page's. The contract list is the section's own and
    // is expected — what must not appear beside them is a read of the *summary*,
    // which arrived as outlet context. The year in the list's key is the proof
    // that it did: 2025 is the summary's first entry, and nothing on this screen
    // asked the server which year that was.
    expect(scopedToThisOrgano).toEqual([
      ['organo', ORGANO_ID],
      ['contratos-menores', ORGANO_ID, 2025],
    ]);
    // And the member read was made exactly once: `nock` refuses an unmatched
    // request, and every interceptor these cases set up is spent.
    expect(scope.isDone()).toBe(true);
    expect(nock.pendingMocks()).toHaveLength(0);
  });

  it('leaves the page to name the Órgano, the section adding no heading of its own', async () => {
    mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));
    mockContracts(2025);

    renderApp(PAGE_PATH);

    await yearChooser();

    // The shell's product name is the only other heading on the screen.
    const headings = screen.getAllByRole('heading').map((heading) => heading.textContent);
    expect(headings).toEqual([strings.appName, ORGANO_NAME]);
  });

  it('answers a family it declares no route for with the shell not-found page', async () => {
    // No read is stubbed because none is made: the page never mounts. The
    // redirect that sends an unknown family to the first one an Órgano holds is
    // the page's, and the page is not what answers this URL — only a family with
    // a child route reaches it. A segment this build does not route to is
    // treated as what it is, an address the app does not have, which is how
    // every other unrecognised URL is answered.
    renderApp(`${PAGE_PATH}/licitacions`);

    expect(await screen.findByText(strings.notFound.title)).toBeInTheDocument();
    // Both negatives are safe here only because the positive above establishes
    // the match: neither chunk is imported on this route, so on their own they
    // would pass by outrunning an import that never starts.
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: ORGANO_NAME })).not.toBeInTheDocument();
  });

  it('routes no path deeper inside the family, because the section declares none', async () => {
    renderApp(`${SECTION_PATH}/algo-mais`);

    // The page's own suite asserts that it keeps the family's tab active for a
    // path deeper than the family segment, against a harness whose child route
    // is a splat. This build's child route is not, so no such path reaches the
    // page at all — and that difference is only visible here.
    //
    // The day the section gains a route of its own, this is the test that goes
    // red, and the child route above is what has to grow a splat or children.
    expect(await screen.findByText(strings.notFound.title)).toBeInTheDocument();
  });

  it('frames an Órgano that holds nothing, even at a family segment', async () => {
    mockOrgano(200, member({}));

    // Newly reachable: before a child route existed this URL fell to the
    // catch-all. A retained link to a family the Órgano has since stopped
    // holding is answered by the page saying so, not by a 404 and not by an
    // empty section.
    renderApp(SECTION_PATH);

    expect(await screen.findByText(pageCopy.noContracts)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });

  it('reports an unknown Órgano at a family segment as not found, not as a bad route', async () => {
    mockOrgano(404, { type: 'urn:conxugal:problem-type:organo-not-found' });

    renderApp(SECTION_PATH);

    expect(await screen.findByText(pageCopy.notFoundTitle)).toBeInTheDocument();
    // The page's answer about the Órgano, not the shell's about the URL.
    expect(screen.queryByText(strings.notFound.title)).not.toBeInTheDocument();
  });
});
