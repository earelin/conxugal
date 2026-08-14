import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../../shared/lib/strings';
import { BASE_URL, mockCurrentUser, mockOrganosPicker, renderApp } from '../../test/renderApp';

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
    mockOrganosPicker();
    renderShell();

    expect(await screen.findByText(strings.nav.adminSection)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.panel })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.users })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });

  it("shows the signed-in user's email, role label and avatar initials in the header", async () => {
    mockCurrentUser('ADMIN');
    mockOrganosPicker();
    renderShell();

    expect(await screen.findByText('admin@conxugal.gal')).toBeInTheDocument();
    expect(screen.getByText(strings.roleLabel.ADMIN)).toBeInTheDocument();
    expect(screen.getByText('AD')).toBeInTheDocument();
  });

  it('keeps the user menu trigger reachable regardless of viewport width', async () => {
    mockCurrentUser('ADMIN');
    mockOrganosPicker();
    renderShell();

    const trigger = await screen.findByRole('button', { name: strings.userMenu.trigger });
    expect(trigger.closest('.mantine-visible-from-sm, .mantine-hidden-from-sm')).toBeNull();
  });

  it('hides the administration section for a USER session', async () => {
    const scope = mockCurrentUser('USER');
    mockOrganosPicker();
    renderShell();

    await waitFor(() => expect(scope.isDone()).toBe(true));

    expect(screen.queryByText(strings.nav.adminSection)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.panel })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.users })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });
});

const picker = strings.organoPicker;

function pickerTrigger() {
  return screen.findByRole('button', { name: `${picker.label} ${picker.placeholder}` });
}

describe('AppLayout Organo picker', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders the picker in the side panel for a resolved authenticated user', async () => {
    mockCurrentUser('USER');
    mockOrganosPicker();
    renderShell();

    expect(await pickerTrigger()).toBeInTheDocument();
  });

  it('places the picker above the primary navigation rather than inside it', async () => {
    mockCurrentUser('USER');
    mockOrganosPicker();
    renderShell();

    const trigger = await pickerTrigger();
    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });

    expect(nav).not.toContainElement(trigger);
    expect(trigger.compareDocumentPosition(nav)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('is absent while the session is unresolved, and reads nothing until it is', async () => {
    const session = mockCurrentUser('USER');
    const reads = mockOrganosPicker();
    renderShell();

    // Before /api/me answers there is no picker and, just as importantly, no
    // request for what it would show.
    expect(screen.queryByText(picker.label)).not.toBeInTheDocument();
    expect(reads.isDone()).toBe(false);

    await waitFor(() => expect(session.isDone()).toBe(true));
    expect(await pickerTrigger()).toBeInTheDocument();
    await waitFor(() => expect(reads.isDone()).toBe(true));
  });

  it('drops down the browse tree assembled from the two reads', async () => {
    const user = userEvent.setup();
    mockCurrentUser('USER');
    mockOrganosPicker();
    renderShell();

    await user.click(await pickerTrigger());

    const tree = await screen.findByRole('tree', { name: picker.label });
    expect(within(tree).getByText('Consellería de Sanidade')).toBeInTheDocument();
    expect(within(tree).getByText('Servizo Galego de Saúde')).toBeInTheDocument();
    // Unclassified, so at the root beside the root terms.
    expect(within(tree).getByText('Instituto Galego da Vivenda e Solo')).toBeInTheDocument();
  });

  it('offers a retry when a read fails, and never an empty tree', async () => {
    const user = userEvent.setup();
    mockCurrentUser('USER');
    nock(BASE_URL).get('/api/organos').reply(500);
    nock(BASE_URL).get('/api/organos/taxonomia').reply(500);
    renderShell();

    await user.click(await pickerTrigger());

    expect(await screen.findByRole('button', { name: strings.retry })).toBeInTheDocument();
    expect(screen.getByText(picker.errorTitle)).toBeInTheDocument();
    expect(screen.queryByText(picker.empty)).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
  });
});
