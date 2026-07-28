import { screen, waitFor } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { mockCurrentUser, renderApp } from '../../test/renderApp';
import { strings } from '../../shared/lib/strings';

function renderShell() {
  return renderApp('/');
}

describe('AppLayout admin navigation', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('shows the administration section for an ADMIN session', async () => {
    mockCurrentUser('ADMIN');
    renderShell();

    expect(await screen.findByText(strings.nav.adminSection)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.panel })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.users })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });

  it("shows the signed-in user's email, role label and avatar initials in the header", async () => {
    mockCurrentUser('ADMIN');
    renderShell();

    expect(await screen.findByText('admin@conxugal.gal')).toBeInTheDocument();
    expect(screen.getByText(strings.roleLabel.ADMIN)).toBeInTheDocument();
    expect(screen.getByText('AD')).toBeInTheDocument();
  });

  it('hides the administration section for a USER session', async () => {
    const scope = mockCurrentUser('USER');
    renderShell();

    await waitFor(() => expect(scope.isDone()).toBe(true));

    expect(screen.queryByText(strings.nav.adminSection)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.panel })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.users })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });
});
