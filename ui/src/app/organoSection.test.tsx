import { screen, within } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../shared/lib/strings';
import { BASE_URL, mockCurrentUser, mockOrganosPicker, renderApp } from '../test/renderApp';

const organo = strings.organo;
const section = strings.contratosMenores;

const ORGANO_ID = 'o-1';
const ORGANO_NAME = 'Servizo Galego de Saúde';
const FAMILY_PATH = 'contratos-menores';
const SECTION_PATH = `/organo/${ORGANO_ID}/${FAMILY_PATH}`;

/**
 * The fixtures are declared here rather than taken from the page slice's
 * harness: the app layer may reach a feature only through its barrel, so a test
 * sitting beside the router cannot borrow one slice's internals to assert about
 * another's.
 */
function mockOrgano() {
  return nock(BASE_URL)
    .get(`/api/organo/${ORGANO_ID}`)
    .reply(200, {
      id: ORGANO_ID,
      name: ORGANO_NAME,
      families: {
        contratosMenores: {
          route: FAMILY_PATH,
          summary: { years: [2025, 2024, 2023], partial: false, updating: true },
        },
      },
    });
}

/**
 * The page and the section are separate chunks, so every entry into this screen
 * settles in two steps: awaiting the chooser is awaiting the second one.
 */
function yearChooser() {
  return screen.findByRole('combobox', { name: section.yearLabel });
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
    mockOrgano();

    const { router } = renderApp(`/organo/${ORGANO_ID}`);

    expect(await yearChooser()).toHaveValue('2025');
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(SECTION_PATH);

    // Inside the page, not beside it: the tab bar is the page's, the chooser is
    // the section's, and the section sits in the panel the active tab owns.
    // Both on screen at once would also be true of a section that had replaced
    // the frame, which is exactly what this route used to do.
    const bar = screen.getByRole('tablist', { name: organo.tabsLabel });
    const tab = within(bar).getByRole('tab', { name: organo.families.contratosMenores });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toContainElement(await yearChooser());
  });

  it('renders the same page for a link straight to the family, without redirecting', async () => {
    mockOrgano();

    const { router } = renderApp(SECTION_PATH);

    expect(await yearChooser()).toHaveValue('2025');
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(SECTION_PATH);
    // Arrived rather than been sent: nothing replaced the entry it opened on.
    expect(router.state.historyAction).toBe('POP');
  });

  it('opens the chooser on the year a deep link names', async () => {
    mockOrgano();

    renderApp(`${SECTION_PATH}?year=2023`);

    expect(await yearChooser()).toHaveValue('2023');
  });

  it("carries the section's own query string through the page's redirect", async () => {
    mockOrgano();

    const { router } = renderApp(`/organo/${ORGANO_ID}?year=2024&sort=amount%2Cdesc&page=3`);

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
    const scope = mockOrgano();

    const { queryClient } = renderApp(`/organo/${ORGANO_ID}`);

    await yearChooser();

    // Serialised rather than filtered on the key's own elements: a section
    // fetching a summary of its own would name the Órgano whatever it called the
    // read, and react-query's idiomatic shape puts it in an options object —
    // `['contratosMenores', { organoId }]` — where neither a check on the key's
    // head nor `Array.includes` would find it.
    const scopedToThisOrgano = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
      .filter((key) => JSON.stringify(key).includes(ORGANO_ID));
    expect(scopedToThisOrgano).toEqual([['organo', ORGANO_ID]]);
    // Nothing else was asked for either: `nock` refuses an unmatched request,
    // and every interceptor this test set up is spent.
    expect(scope.isDone()).toBe(true);
    expect(nock.pendingMocks()).toHaveLength(0);
  });

  it('leaves the page to name the Órgano, the section adding no heading of its own', async () => {
    mockOrgano();

    renderApp(`/organo/${ORGANO_ID}`);

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
    renderApp(`/organo/${ORGANO_ID}/licitacions`);

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
    nock(BASE_URL)
      .get(`/api/organo/${ORGANO_ID}`)
      .reply(200, { id: ORGANO_ID, name: ORGANO_NAME, families: {} });

    // Newly reachable: before a child route existed this URL fell to the
    // catch-all. A retained link to a family the Órgano has since stopped
    // holding is answered by the page saying so, not by a 404 and not by an
    // empty section.
    renderApp(SECTION_PATH);

    expect(await screen.findByText(organo.noContracts)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });

  it('reports an unknown Órgano at a family segment as not found, not as a bad route', async () => {
    nock(BASE_URL)
      .get(`/api/organo/${ORGANO_ID}`)
      .reply(404, { type: 'urn:conxugal:problem-type:organo-not-found' });

    renderApp(SECTION_PATH);

    expect(await screen.findByText(organo.notFoundTitle)).toBeInTheDocument();
    // The page's answer about the Órgano, not the shell's about the URL.
    expect(screen.queryByText(strings.notFound.title)).not.toBeInTheDocument();
  });
});
