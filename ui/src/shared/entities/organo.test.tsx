import { QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import nock from 'nock';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { HttpError } from '../lib/httpClient';
import { createQueryClient } from '../lib/queryClient';
import { useOrgano } from './organo';

const BASE_URL = 'http://localhost:3000';
const ORGANO_ID = '7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71';

const member = {
  id: ORGANO_ID,
  name: 'Servizo Galego de Saúde',
  families: {
    contratosMenores: {
      route: 'contratos-menores',
      summary: { years: [2025, 2024, 2023], partial: false, updating: true },
    },
  },
};

function createWrapper() {
  const queryClient = createQueryClient();
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { Wrapper, queryClient };
}

function renderUseOrgano(id = ORGANO_ID) {
  const { Wrapper } = createWrapper();
  return renderHook(() => useOrgano(id), { wrapper: Wrapper });
}

describe('useOrgano', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('reads one Órgano and every family entry it carries', async () => {
    nock(BASE_URL).get(`/api/organo/${ORGANO_ID}`).reply(200, member);

    const { result } = renderUseOrgano();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(member);
  });

  it('keeps an id that needs escaping inside one path segment', async () => {
    const scope = nock(BASE_URL)
      .get('/api/organo/a%2Fb')
      .reply(200, { ...member, id: 'a/b' });

    renderUseOrgano('a/b');

    await waitFor(() => expect(scope.isDone()).toBe(true));
  });

  it('reports an unknown Órgano as a 404 without asking again', async () => {
    nock(BASE_URL)
      .get(`/api/organo/${ORGANO_ID}`)
      .reply(
        404,
        { type: 'urn:conxugal:problem-type:organo-not-found', title: 'Órgano not found' },
        { 'Content-Type': 'application/problem+json' },
      );
    // Only ever consumed by a retry, which a 404 must not provoke: an id the
    // catalogue does not know will not become known by being asked twice.
    nock(BASE_URL).get(`/api/organo/${ORGANO_ID}`).reply(200, member);

    const { result } = renderUseOrgano();

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(HttpError);
    expect((result.current.error as HttpError).status).toBe(404);
    expect(result.current.data).toBeUndefined();
    expect(nock.pendingMocks()).toHaveLength(1);
  });

  it('reports a read that failed for any other reason under its own status', async () => {
    nock(BASE_URL).get(`/api/organo/${ORGANO_ID}`).reply(500);

    const { result } = renderUseOrgano();

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as HttpError).status).toBe(500);
  });
});
