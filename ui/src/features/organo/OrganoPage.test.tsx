import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../../shared/lib/strings';
import { mockCurrentUser, mockOrganosPicker, renderApp } from '../../test/renderApp';
import {
  contratosMenores,
  copy,
  familyEntry,
  HOLDS_CONTRATOS_MENORES,
  member,
  mockOrgano,
  ORGANO_ID,
  ORGANO_NAME,
  organoHeading,
  renderOrganoPage,
  retryButton,
} from './organoHarness';

describe('OrganoPage', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  describe('the four states, which must not render alike', () => {
    it('says it is loading while the one read is in flight', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      renderOrganoPage();

      expect(screen.getByRole('status')).toHaveTextContent(strings.loading);
      expect(organoHeading()).not.toBeInTheDocument();

      await screen.findByRole('tablist');
    });

    it('states plainly that an Órgano holds nothing, with no tab bar and no retry', async () => {
      mockOrgano(200, member({}));

      renderOrganoPage();

      expect(await screen.findByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
      expect(screen.getByText(copy.noContracts)).toBeInTheDocument();
      expect(screen.getByText(copy.noContractsHelp)).toBeInTheDocument();
      expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(retryButton()).not.toBeInTheDocument();
    });

    it('reports a failed read as a failure worth trying again, naming nothing it did not get', async () => {
      mockOrgano(500);

      renderOrganoPage();

      expect(await screen.findByText(copy.errorTitle)).toBeInTheDocument();
      expect(screen.getByText(copy.errorHelp)).toBeInTheDocument();
      expect(retryButton()).toBeInTheDocument();
      // The name and the tabs came from the same response, and it did not land.
      expect(organoHeading()).not.toBeInTheDocument();
      expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
      expect(screen.queryByText(copy.noContracts)).not.toBeInTheDocument();
    });

    it('reads the page again when the reader asks, and renders it once it answers', async () => {
      const user = userEvent.setup();
      mockOrgano(500);
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      renderOrganoPage();

      await user.click(await screen.findByRole('button', { name: strings.retry }));

      expect(await screen.findByRole('heading', { name: ORGANO_NAME })).toBeInTheDocument();
      expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
    });

    it('says an unknown Órgano was not found, and offers nothing to try again', async () => {
      mockOrgano(404, { type: 'urn:conxugal:problem-type:organo-not-found' });

      renderOrganoPage();

      expect(await screen.findByText(copy.notFoundTitle)).toBeInTheDocument();
      expect(screen.getByText(copy.notFoundHelp)).toBeInTheDocument();
      expect(retryButton()).not.toBeInTheDocument();
      // An id that does not exist is a third thing: neither a page holding no
      // contracts nor a read that could not be made.
      expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
      expect(screen.queryByText(copy.noContracts)).not.toBeInTheDocument();
    });
  });

  describe('the bar, which is a function of the read', () => {
    it('draws the family the Órgano holds and no other', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      renderOrganoPage();

      const tabs = await screen.findAllByRole('tab');
      expect(tabs).toHaveLength(1);
      expect(tabs[0]).toHaveAccessibleName(copy.families.contratosMenores);
      expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    });

    it('draws no tab for a family key this build does not know, and never lands on it', async () => {
      mockOrgano(200, member({ licitacions: familyEntry }));

      const { router } = renderOrganoPage();

      expect(await screen.findByText(copy.noContracts)).toBeInTheDocument();
      expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}`);
    });

    it('titles the page with the Órgano and adds no subtitle of its own', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      renderOrganoPage();

      const title = await screen.findByRole('heading', { name: ORGANO_NAME });
      expect(screen.getAllByRole('heading')).toHaveLength(1);
      // The tab bar is what follows the name — a subtitle would sit between
      // them, and being a `Text` rather than a heading would slip past a count.
      expect(title.nextElementSibling).toContainElement(
        screen.getByRole('tablist', { name: copy.tabsLabel }),
      );
    });
  });

  describe('the family a URL lands on', () => {
    it('sends the bare path to the first family the Órgano holds', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const { router } = renderOrganoPage(`/organo/${ORGANO_ID}`);

      await screen.findByRole('tablist');
      expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${contratosMenores.path}`);
    });

    it('sends a family segment with no tab to that same family, rather than an empty panel', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const { router } = renderOrganoPage(`/organo/${ORGANO_ID}/licitacions`);

      await screen.findByRole('tablist');
      expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${contratosMenores.path}`);
      expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
    });

    it('leaves a path deeper inside the active family where it is', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const deep = `/organo/${ORGANO_ID}/${contratosMenores.path}/algo-mais`;
      const { router } = renderOrganoPage(deep);

      await screen.findByRole('tablist');
      expect(router.state.location.pathname).toBe(deep);
      expect(screen.getByRole('tab', { name: copy.families.contratosMenores })).toHaveAttribute(
        'aria-selected',
        'true',
      );
    });

    it('carries the query string through the redirect, since it is the section that owns it', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const { router } = renderOrganoPage(`/organo/${ORGANO_ID}?ano=2024`);

      await screen.findByRole('tablist');
      expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${contratosMenores.path}`);
      expect(router.state.location.search).toBe('?ano=2024');
    });

    it('replaces the bare path rather than stacking it, so Back leaves the page', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const { router } = renderOrganoPage();

      await screen.findByRole('tablist');
      expect(router.state.historyAction).toBe('REPLACE');
    });
  });

  describe('what reaches the section', () => {
    it("hands the active family's summary to the outlet, without a second request", async () => {
      const scope = mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      renderOrganoPage();

      expect(await screen.findByText(`${ORGANO_NAME} abre en 2025`)).toBeInTheDocument();
      // Nothing else was asked for: `nock` refuses an unmatched request, and the
      // one interceptor this test set up is spent.
      expect(scope.isDone()).toBe(true);
      expect(nock.pendingMocks()).toHaveLength(0);
    });
  });

  describe('mounted by the application router', () => {
    beforeEach(() => {
      mockCurrentUser('USER');
      mockOrganosPicker();
    });

    it('answers /organo/:id, redirecting to the family it holds', async () => {
      mockOrgano(200, member(HOLDS_CONTRATOS_MENORES));

      const { queryClient, router } = renderApp(`/organo/${ORGANO_ID}`);

      await waitFor(() =>
        expect(router.state.location.pathname).toBe(
          `/organo/${ORGANO_ID}/${contratosMenores.path}`,
        ),
      );
      // No child route is declared yet, so the shell's catch-all answers the
      // redirect. The page is a frame with no interior until the first section
      // is mounted into it.
      expect(await screen.findByText(strings.notFound.title)).toBeInTheDocument();

      const readKeys = queryClient
        .getQueryCache()
        .getAll()
        .map((query) => query.queryKey)
        .filter((key) => key[0] === 'organo');
      expect(readKeys).toEqual([['organo', ORGANO_ID]]);
    });
  });
});
