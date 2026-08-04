import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import type { Organo, Termo } from './organos';
import { OrganosPage } from './OrganosPage';

const BASE_URL = 'http://localhost:3000';
const ORGANO_PATH = '/api/admin/organo';
const copy = strings.admin.organos.assign;

const consellerias: Termo = { id: 't-1', name: 'Consellerías', parentId: null };
const sanidade: Termo = { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' };
const educacion: Termo = { id: 't-3', name: 'Consellería de Educación', parentId: 't-1' };
const concellos: Termo = { id: 't-4', name: 'Concellos', parentId: null };

const TAXONOMIA = [consellerias, sanidade, educacion, concellos];

const sergas: Organo = { id: 'o-1', name: 'Servizo Galego de Saúde', active: true, termoId: 't-2' };
const cunqueiro: Organo = {
  id: 'o-2',
  name: 'Hospital Álvaro Cunqueiro',
  active: false,
  termoId: 't-2',
};
const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
};
const turismo: Organo = {
  id: 'o-4',
  name: 'Axencia de Turismo de Galicia',
  active: false,
  termoId: null,
};

const CATALOGUE = [sergas, cunqueiro, vivenda, turismo];

/** The same catalogue with one Órgano filed somewhere else. */
function filedIn(organo: Organo, termoId: string | null): Organo[] {
  return CATALOGUE.map((entry) => (entry.id === organo.id ? { ...entry, termoId } : entry));
}

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/organos').reply(200, organos);
}

function mockTaxonomia(termos: Termo[]) {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, termos);
}

/**
 * A refusal has to arrive as `application/problem+json` or the client parses it
 * as a bare `HttpError`, the problem `type` is lost, and both messages degrade
 * to the generic one — which is the bug these tests exist to rule out.
 */
function refuses(status: number, type: string) {
  return [
    status,
    { type: `urn:conxugal:problem-type:${type}`, title: type, status },
    { 'Content-Type': 'application/problem+json' },
  ] as const;
}

/**
 * `env="test"` is Mantine's own switch for jsdom: the Órgano picker is a
 * `Combobox`, whose floating dropdown the `hide` middleware would keep hidden
 * because every element in jsdom measures zero.
 */
function renderOrganosPage() {
  return render(
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={createQueryClient()}>
        <OrganosPage />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

const tree = () => screen.getByRole('tree');
const dialog = () => screen.findByRole('dialog');

async function openTermo(user: UserEvent, name: string) {
  await user.click(await within(await screen.findByRole('tree')).findByText(name));
}

async function openWorklist(user: UserEvent) {
  await user.click(
    screen.getByRole('button', { name: new RegExp(strings.admin.organos.unclassified) }),
  );
}

/** The table of whichever pane is open, worklist or term. */
function paneTable(): HTMLElement {
  return screen.getByRole('table');
}

function rowFor(name: string): HTMLElement {
  return within(paneTable()).getByText(name).closest('tr') as HTMLElement;
}

/** The cell carrying the name, which is where a row's dimming is applied. */
function nameCellOf(name: string): HTMLElement {
  return within(rowFor(name)).getAllByRole('cell')[0];
}

/** The Órgano named in each body row, which is what a pane's contents are. */
const rowNames = (): (string | null)[] =>
  within(paneTable())
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[0].textContent);

/**
 * `Asignar` names the worklist row action, the term header's button and the
 * dialog's own primary alike, so every entry point is reached through the row
 * or pane it belongs to rather than by name alone.
 */
async function assignFromRow(user: UserEvent, organo: Organo) {
  await user.click(
    within(rowFor(organo.name)).getByRole('button', {
      name: `${copy.fromWorklist}: ${organo.name}`,
    }),
  );
}

async function clearFromRow(user: UserEvent, organo: Organo) {
  await user.click(
    within(rowFor(organo.name)).getByRole('button', { name: `${copy.clear}: ${organo.name}` }),
  );
}

async function chooseTermo(user: UserEvent, name: string) {
  const list = within(await dialog()).getByRole('listbox', { name: copy.termoLabel });
  await user.click(within(list).getByRole('option', { name }));
}

async function chooseOrgano(user: UserEvent, name: string) {
  await user.click(within(await dialog()).getByRole('combobox', { name: copy.organoLabel }));
  // `renderOption` puts the placement inside the option's accessible name, so
  // the name is matched by its opening rather than in full.
  await user.click(await screen.findByRole('option', { name: new RegExp(`^${name}`) }));
}

async function confirmAssign(user: UserEvent) {
  await user.click(within(await dialog()).getByRole('button', { name: copy.submit }));
}

async function showSection() {
  await screen.findByText(consellerias.name);
}

describe('órgano classification', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('holds exactly the unfiled Órganos in the worklist, and asks for nothing extra', async () => {
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await showSection();

    expect(rowNames()).toEqual([vivenda.name, turismo.name]);
    expect(
      screen.getByText(`2 ${strings.admin.organos.countUnclassified.plural}`),
    ).toBeInTheDocument();
    // The worklist is the null-termoId slice of the two reads above, not a third
    // request: `disableNetConnect` rejects any other call, and a rejected read
    // would have replaced the whole section with its error alert.
    expect(nock.pendingMocks()).toEqual([]);
    expect(screen.queryByText(strings.admin.organos.errorTitle)).not.toBeInTheDocument();
  });

  it('files an Órgano from the worklist, which leaves it in the same refresh', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, sanidade.name);

    const put = nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`, { termoId: sanidade.id })
      .reply(204);
    mockCatalogue(filedIn(vivenda, sanidade.id));

    await confirmAssign(user);

    await waitFor(() => {
      expect(rowNames()).toEqual([turismo.name]);
    });
    expect(put.isDone()).toBe(true);

    await openTermo(user, sanidade.name);
    expect(rowNames()).toContain(vivenda.name);
  });

  it('moves an already-filed Órgano rather than adding a second placement', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    // The term's own header is the way in: it fixes the destination and asks
    // which Órgano to pull across.
    await openTermo(user, educacion.name);
    await user.click(screen.getByRole('button', { name: copy.fromTermo }));
    await chooseOrgano(user, sergas.name);

    const put = nock(BASE_URL)
      .put(`${ORGANO_PATH}/${sergas.id}/termo`, { termoId: educacion.id })
      .reply(204);
    mockCatalogue(filedIn(sergas, educacion.id));

    await confirmAssign(user);

    await waitFor(() => {
      expect(rowNames()).toEqual([sergas.name]);
    });
    expect(put.isDone()).toBe(true);

    // Under the new term and under no other: a placement is replaced, never added.
    await openTermo(user, sanidade.name);
    expect(rowNames()).toEqual([cunqueiro.name]);
  });

  it('states where an Órgano currently sits, so a reassignment is legible first', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);

    expect(
      within(await dialog()).getByText(copy.currently(strings.admin.organos.unclassified)),
    ).toBeInTheDocument();
  });

  it('returns a cleared Órgano to the worklist without deleting it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, sanidade.name);
    expect(rowNames()).toEqual([sergas.name, cunqueiro.name]);

    const del = nock(BASE_URL).delete(`${ORGANO_PATH}/${sergas.id}/termo`).reply(204);
    mockCatalogue(filedIn(sergas, null));

    await clearFromRow(user, sergas);

    await waitFor(() => {
      expect(rowNames()).toEqual([cunqueiro.name]);
    });
    expect(del.isDone()).toBe(true);

    await openWorklist(user);
    expect(rowNames()).toEqual([sergas.name, vivenda.name, turismo.name]);
  });

  it('offers no clear in the worklist, where a row has no placement to take away', async () => {
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    expect(
      within(rowFor(vivenda.name)).queryByRole('button', { name: new RegExp(copy.clear) }),
    ).not.toBeInTheDocument();
  });

  it('says the target term is gone when another admin has just deleted it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, concellos.name);

    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'termo-not-found'));

    await confirmAssign(user);

    expect(await within(await dialog()).findByText(copy.termoNotFound)).toBeInTheDocument();
  });

  it('says the Órgano is gone when that is the id the server could not find', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, sanidade.name);

    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'organo-not-found'));

    await confirmAssign(user);

    const alert = await within(await dialog()).findByText(copy.organoNotFound);

    expect(alert).toBeInTheDocument();
    expect(within(await dialog()).queryByText(copy.termoNotFound)).not.toBeInTheDocument();
  });

  it('recovers from a refusal by re-reading the section', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, concellos.name);
    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'termo-not-found'));
    await confirmAssign(user);
    await within(await dialog()).findByText(copy.termoNotFound);

    // The term really is gone, so the refreshed taxonomía no longer carries it.
    const remaining = TAXONOMIA.filter((termo) => termo.id !== concellos.id);
    const catalogue = mockCatalogue(CATALOGUE);
    const taxonomia = mockTaxonomia(remaining);

    await user.click(within(await dialog()).getByRole('button', { name: copy.refresh }));

    await waitFor(() => {
      expect(within(tree()).queryByText(concellos.name)).not.toBeInTheDocument();
    });
    expect(catalogue.isDone()).toBe(true);
    expect(taxonomia.isDone()).toBe(true);

    // The refusal asked for the re-read; once it has happened the message has
    // nothing left to ask for.
    const open = await dialog();

    expect(within(open).queryByText(copy.termoNotFound)).not.toBeInTheDocument();
    // The choice went with the term. Left held, it would keep the primary
    // enabled over a destination the picker no longer shows, re-submitting the
    // same doomed pair for as long as the reader kept clicking.
    expect(
      within(within(open).getByRole('listbox', { name: copy.termoLabel })).queryAllByRole(
        'option',
        {
          selected: true,
        },
      ),
    ).toEqual([]);
    expect(within(open).getByRole('button', { name: copy.submit })).toBeDisabled();
  });

  it('lets the reader pick again after the refresh, and files it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, concellos.name);
    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'termo-not-found'));
    await confirmAssign(user);
    await within(await dialog()).findByText(copy.termoNotFound);

    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA.filter((termo) => termo.id !== concellos.id));
    await user.click(within(await dialog()).getByRole('button', { name: copy.refresh }));
    await waitFor(() => {
      expect(within(tree()).queryByText(concellos.name)).not.toBeInTheDocument();
    });

    await chooseTermo(user, sanidade.name);
    const put = nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`, { termoId: sanidade.id })
      .reply(204);
    mockCatalogue(filedIn(vivenda, sanidade.id));

    await confirmAssign(user);

    await waitFor(() => {
      expect(put.isDone()).toBe(true);
    });
  });

  it('reports a refused clear above the table it failed to change', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, sanidade.name);
    nock(BASE_URL)
      .delete(`${ORGANO_PATH}/${sergas.id}/termo`)
      .reply(...refuses(404, 'organo-not-found'));

    await clearFromRow(user, sergas);

    expect(await screen.findByText(copy.organoNotFound)).toBeInTheDocument();
    expect(rowNames()).toEqual([sergas.name, cunqueiro.name]);
  });

  it('leaves a refused clear behind with the pane it was attempted in', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, sanidade.name);
    nock(BASE_URL)
      .delete(`${ORGANO_PATH}/${sergas.id}/termo`)
      .reply(...refuses(404, 'organo-not-found'));
    await clearFromRow(user, sergas);
    await screen.findByText(copy.organoNotFound);

    // The message is about an Órgano the next pane does not even list.
    await openTermo(user, educacion.name);

    expect(screen.queryByText(copy.organoNotFound)).not.toBeInTheDocument();
  });

  it('takes a refused clear down once the refresh it asked for has happened', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, sanidade.name);
    nock(BASE_URL)
      .delete(`${ORGANO_PATH}/${sergas.id}/termo`)
      .reply(...refuses(404, 'organo-not-found'));
    await clearFromRow(user, sergas);
    await screen.findByText(copy.organoNotFound);

    mockCatalogue(filedIn(sergas, null));
    mockTaxonomia(TAXONOMIA);

    await user.click(screen.getByRole('button', { name: copy.refresh }));

    // Without this the alert is undismissable: its own button re-reads the
    // section and leaves the message sitting over the result.
    await waitFor(() => {
      expect(screen.queryByText(copy.organoNotFound)).not.toBeInTheDocument();
    });
  });

  it('keeps an inactive Órgano dimmed, listed and still filed and unfiled at will', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    // Dimmed rather than hidden, and dimmed in the sense SPEC-0004 means:
    // asserting only the state badge would leave the opacity free to be dropped.
    expect(rowFor(turismo.name)).toHaveTextContent(strings.admin.organos.stateInactive);
    expect(nameCellOf(turismo.name)).toHaveStyle({ opacity: '0.6' });
    expect(nameCellOf(vivenda.name)).not.toHaveStyle({ opacity: '0.6' });

    await assignFromRow(user, turismo);
    await chooseTermo(user, sanidade.name);
    const put = nock(BASE_URL)
      .put(`${ORGANO_PATH}/${turismo.id}/termo`, { termoId: sanidade.id })
      .reply(204);
    mockCatalogue(filedIn(turismo, sanidade.id));
    await confirmAssign(user);

    await waitFor(() => {
      expect(rowNames()).toEqual([vivenda.name]);
    });
    expect(put.isDone()).toBe(true);

    await openTermo(user, sanidade.name);
    const del = nock(BASE_URL).delete(`${ORGANO_PATH}/${cunqueiro.id}/termo`).reply(204);
    mockCatalogue(filedIn(cunqueiro, null));

    await clearFromRow(user, cunqueiro);

    await waitFor(() => {
      expect(del.isDone()).toBe(true);
    });
  });

  it('filters the term picker without accents, and never hides the current choice', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    const list = within(await dialog()).getByRole('listbox', { name: copy.termoLabel });
    await chooseTermo(user, sanidade.name);

    await user.type(
      within(await dialog()).getByRole('textbox', { name: copy.searchLabel }),
      'educacion',
    );

    await waitFor(() => {
      expect(within(list).getByRole('option', { name: educacion.name })).toBeInTheDocument();
    });
    // The branch the match hangs off stays, and so does the term already chosen.
    expect(within(list).getByRole('option', { name: consellerias.name })).toBeInTheDocument();
    expect(within(list).getByRole('option', { name: sanidade.name })).toBeInTheDocument();
    expect(within(list).queryByRole('option', { name: concellos.name })).not.toBeInTheDocument();
    expect(within(list).getAllByRole('option', { selected: true })).toHaveLength(1);
  });

  it('says the taxonomía is empty rather than blaming an empty search', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia([]);
    renderOrganosPage();
    await screen.findByText(vivenda.name);

    await assignFromRow(user, vivenda);

    // The state right after a first import, when every row is in the worklist
    // and this is the first dialog anyone opens.
    expect(await within(await dialog()).findByText(strings.admin.organos.treeEmpty)).toBeVisible();
    expect(within(await dialog()).queryByText(copy.noTermoMatches(''))).not.toBeInTheDocument();
  });

  it('walks the term picker with the keyboard alone', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    const list = within(await dialog()).getByRole('listbox', { name: copy.termoLabel });

    within(list).getByRole('option', { name: consellerias.name }).focus();
    await user.keyboard('{ArrowDown}');
    await user.keyboard('{Enter}');

    expect(within(list).getByRole('option', { name: sanidade.name })).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('filters the Órgano picker without accents, and on the name alone', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, educacion.name);
    await user.click(screen.getByRole('button', { name: copy.fromTermo }));
    const field = within(await dialog()).getByRole('combobox', { name: copy.organoLabel });
    await user.click(field);

    // Mantine's own filter compares the raw label, so «saude» would miss
    // «Servizo Galego de Saúde» entirely.
    await user.type(field, 'saude');

    await waitFor(() => {
      expect(screen.getByRole('option', { name: new RegExp(`^${sergas.name}`) })).toBeVisible();
    });
    expect(
      screen.queryByRole('option', { name: new RegExp(`^${vivenda.name}`) }),
    ).not.toBeInTheDocument();
  });

  it('does not match an Órgano on the term it happens to sit in', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, educacion.name);
    await user.click(screen.getByRole('button', { name: copy.fromTermo }));
    const field = within(await dialog()).getByRole('combobox', { name: copy.organoLabel });
    await user.click(field);

    // Every option states its placement, and SERGAS sits under a Consellería.
    // Matching on that would bury the Órgano actually being typed.
    await user.type(field, 'consellería');

    await waitFor(() => {
      expect(screen.getByText(copy.noOrganoMatches)).toBeVisible();
    });
  });

  it('closes the dialog when the Órgano it is about is deleted under it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, sanidade.name);
    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'organo-not-found'));
    await confirmAssign(user);
    await within(await dialog()).findByText(copy.organoNotFound);

    // The refusal was the truth: the Órgano really is gone from the catalogue.
    mockCatalogue(CATALOGUE.filter((organo) => organo.id !== vivenda.id));
    mockTaxonomia(TAXONOMIA);

    await user.click(within(await dialog()).getByRole('button', { name: copy.refresh }));

    // Nothing to file any more, so the dialog has nothing to be about.
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(rowNames()).toEqual([turismo.name]);
  });

  it('closes the dialog when the term it is filing into is deleted under it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, educacion.name);
    await user.click(screen.getByRole('button', { name: copy.fromTermo }));
    await chooseOrgano(user, vivenda.name);
    nock(BASE_URL)
      .put(`${ORGANO_PATH}/${vivenda.id}/termo`)
      .reply(...refuses(404, 'termo-not-found'));
    await confirmAssign(user);
    await within(await dialog()).findByText(copy.termoNotFound);

    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA.filter((termo) => termo.id !== educacion.id));

    await user.click(within(await dialog()).getByRole('button', { name: copy.refresh }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    // The pane it was opened from falls back to the worklist rather than
    // showing a term that no longer exists.
    expect(
      await screen.findByRole('heading', { name: strings.admin.organos.unclassified }),
    ).toBeVisible();
  });

  it('states an inactive Órgano as such in the picker, and still offers it', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await openTermo(user, educacion.name);
    await user.click(screen.getByRole('button', { name: copy.fromTermo }));
    await user.click(within(await dialog()).getByRole('combobox', { name: copy.organoLabel }));

    const option = await screen.findByRole('option', {
      name: new RegExp(`^${turismo.name} \\(${copy.inactive} · `),
    });

    expect(option).toBeInTheDocument();
    expect(option).not.toHaveAttribute('data-disabled');
  });

  it('sends nothing when the dialog is cancelled', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();
    await showSection();

    await assignFromRow(user, vivenda);
    await chooseTermo(user, sanidade.name);

    // Registered so the assertion has something to be false about: an unmocked
    // PUT would be rejected by `disableNetConnect` and swallowed into the
    // unmounted form's error state, leaving nothing behind to catch.
    const put = nock(BASE_URL).put(`${ORGANO_PATH}/${vivenda.id}/termo`).reply(204);

    await user.click(
      within(await dialog()).getByRole('button', { name: strings.admin.organos.termo.cancel }),
    );

    expect(put.isDone()).toBe(false);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
