import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import type { Organo, Termo } from './organos';
import { OrganosPage } from './OrganosPage';

const BASE_URL = 'http://localhost:3000';
const TERMOS_PATH = '/api/admin/organos/taxonomia/termos';
const TERMO_PATH = '/api/admin/organos/taxonomia/termo';
const copy = strings.admin.organos.termo;

const consellerias: Termo = { id: 't-1', name: 'Consellerías', parentId: null };
const sanidade: Termo = { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' };
const concellos: Termo = { id: 't-3', name: 'Concellos', parentId: null };
const innovacion: Termo = { id: 't-4', name: 'Axencia Galega de Innovación', parentId: 't-2' };

const TAXONOMIA = [consellerias, sanidade, concellos];

const sergas: Organo = { id: 'o-1', name: 'Servizo Galego de Saúde', active: true, termoId: 't-2' };
const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
};

const CATALOGUE = [sergas, vivenda];

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/organos').reply(200, organos);
}

function mockTaxonomia(termos: Termo[]) {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, termos);
}

/**
 * A refusal has to arrive as `application/problem+json` or the client parses it
 * as a bare `HttpError`, the problem `type` is lost, and every one of these
 * messages degrades to the generic one — which is the bug these tests exist to
 * rule out.
 */
function problemBody(type: string, status: number) {
  return { type: `urn:conxugal:problem-type:${type}`, title: type, status };
}

const PROBLEM_HEADERS = { 'Content-Type': 'application/problem+json' };

/**
 * `env="test"` is Mantine's own switch for jsdom, and this is the first suite
 * that needs it: the parent picker is a `Combobox`, whose floating dropdown is
 * hidden by the `hide` middleware because every element in jsdom measures zero,
 * so its options would be unreachable at any wait.
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

// A dialog opens behind a transition, so every dialog query waits for it. The
// three actions are also named the same on the tree row, in the content header
// and on the dialog's own primary, which is why assertions inside a dialog are
// scoped to it rather than matched by name alone.
const dialog = () => screen.findByRole('dialog');

async function openTermo(user: UserEvent, name: string) {
  const node = await within(await screen.findByRole('tree')).findByText(name);
  await user.click(node);
}

/** Says which of the two entry points into the same dialog a test is driving. */
function paneAction(name: string): HTMLElement {
  return screen.getAllByRole('button', { name }).filter((button) => !tree().contains(button))[0];
}

function treeAction(name: string): HTMLElement {
  return within(tree()).getByRole('button', { name });
}

function nameField(): Promise<HTMLElement> {
  return screen.findByRole('textbox', { name: new RegExp(copy.nameLabel) });
}

async function typeName(user: UserEvent, name: string) {
  const field = await nameField();
  await user.clear(field);
  await user.type(field, name);
}

async function chooseParent(user: UserEvent, option: string) {
  await user.click(await screen.findByRole('combobox', { name: copy.moveParentLabel }));
  await user.click(await screen.findByRole('option', { name: option }));
}

async function submit(user: UserEvent, name: string) {
  await user.click(within(await dialog()).getByRole('button', { name }));
}

describe('taxonomía management', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('creates a term at the root and opens it, without a manual reload', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await screen.findByText(consellerias.name);
    await user.click(screen.getByRole('button', { name: copy.create }));
    await typeName(user, 'Deputacións');

    const created: Termo = { id: 't-9', name: 'Deputacións', parentId: null };
    const post = nock(BASE_URL)
      .post(TERMOS_PATH, { name: 'Deputacións', parentId: null })
      .reply(201, created);
    mockTaxonomia([...TAXONOMIA, created]);

    await submit(user, copy.createSubmit);

    // The 201 carries the new id, so the term opens off the create response
    // rather than a second lookup once the taxonomía comes back.
    expect(await screen.findByRole('heading', { name: created.name })).toBeInTheDocument();
    expect(within(tree()).getByText(created.name)).toBeInTheDocument();
    expect(post.isDone()).toBe(true);
  });

  it('offers the open term as the parent, so creating under it is one click', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(screen.getByRole('button', { name: copy.create }));
    await typeName(user, 'Hospitais');

    const created: Termo = { id: 't-9', name: 'Hospitais', parentId: sanidade.id };
    const post = nock(BASE_URL)
      .post(TERMOS_PATH, { name: 'Hospitais', parentId: sanidade.id })
      .reply(201, created);
    mockTaxonomia([...TAXONOMIA, created]);

    await submit(user, copy.createSubmit);

    expect(await screen.findByRole('heading', { name: created.name })).toBeInTheDocument();
    expect(post.isDone()).toBe(true);
  });

  it('renames the open term from the content header', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.rename));
    await typeName(user, 'Sanidade');

    const renamed = { ...sanidade, name: 'Sanidade' };
    const patch = nock(BASE_URL)
      .patch(`${TERMO_PATH}/${sanidade.id}`, { name: 'Sanidade' })
      .reply(200, renamed);
    mockTaxonomia([consellerias, renamed, concellos]);

    await submit(user, copy.renameSubmit);

    expect(await screen.findByRole('heading', { name: 'Sanidade' })).toBeInTheDocument();
    expect(patch.isDone()).toBe(true);
  });

  it('moves a term under another parent from the selected tree row', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(treeAction(copy.move));
    await chooseParent(user, concellos.name);

    const put = nock(BASE_URL)
      .put(`${TERMO_PATH}/${sanidade.id}/parent`, { parentId: concellos.id })
      .reply(204);
    const moved = { ...sanidade, parentId: concellos.id };
    mockTaxonomia([consellerias, moved, concellos]);

    await submit(user, copy.moveSubmit);

    expect(
      await within(await screen.findByRole('tree')).findByText(concellos.name),
    ).toBeInTheDocument();
    expect(put.isDone()).toBe(true);
  });

  it('moves a term back to the root, which it states as an explicit null parent', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.move));
    await chooseParent(user, copy.moveParentRoot);

    // The root is a destination, not the absence of one: the field is required
    // and the body carries a null rather than leaving it out.
    const put = nock(BASE_URL)
      .put(`${TERMO_PATH}/${sanidade.id}/parent`, { parentId: null })
      .reply(204);
    mockTaxonomia([consellerias, { ...sanidade, parentId: null }, concellos]);

    await submit(user, copy.moveSubmit);

    expect(await screen.findByRole('heading', { name: sanidade.name })).toBeInTheDocument();
    expect(put.isDone()).toBe(true);
  });

  it('deletes an empty term and returns its Órganos to the unclassified worklist', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.delete));

    expect(await screen.findByText(copy.deleteConfirm(sanidade.name))).toBeInTheDocument();

    const remove = nock(BASE_URL).delete(`${TERMO_PATH}/${sanidade.id}`).reply(204);
    // Both reads come back: the term's Órganos are now unclassified, so the
    // catalogue is as stale as the taxonomía.
    mockCatalogue([{ ...sergas, termoId: null }, vivenda]);
    mockTaxonomia([consellerias, concellos]);

    await submit(user, copy.deleteSubmit);

    // The deleted term is gone and its parent is open, which is where the
    // administrator was working.
    expect(await screen.findByRole('heading', { name: consellerias.name })).toBeInTheDocument();
    expect(within(tree()).queryByText(sanidade.name)).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: new RegExp(strings.admin.organos.unclassified) }),
    );

    // Its Órganos are back in the worklist in the same refresh; none disappears.
    expect(await screen.findByText(sergas.name)).toBeInTheDocument();
    expect(screen.getByText(vivenda.name)).toBeInTheDocument();
    expect(remove.isDone()).toBe(true);
  });

  it('explains a move onto a descendant instead of hiding the destination', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia([...TAXONOMIA, innovacion]);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.move));

    // The refusal has to be reachable through the picker: filtering the target
    // out would leave the administrator guessing why an obvious destination is
    // missing.
    await chooseParent(user, `${consellerias.name} › ${sanidade.name} › ${innovacion.name}`);

    expect(
      await screen.findByText(copy.cycleUnderChild(innovacion.name, sanidade.name)),
    ).toBeVisible();
    expect(within(await dialog()).getByRole('button', { name: copy.moveSubmit })).toBeDisabled();
    // Visibly its own message, not the other 409 this section can raise.
    expect(screen.queryByText(copy.hasChildrenOne(innovacion.name))).not.toBeInTheDocument();
    expect(nock.pendingMocks()).toEqual([]);
  });

  it('explains a move onto the term itself, which is not a child of itself', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.move));
    await chooseParent(user, `${consellerias.name} › ${sanidade.name}`);

    expect(await screen.findByText(copy.cycleUnderSelf(sanidade.name))).toBeVisible();
    expect(within(await dialog()).getByRole('button', { name: copy.moveSubmit })).toBeDisabled();
  });

  it('explains a cycle the server refuses, for a tree the browser had gone stale on', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.move));
    await chooseParent(user, concellos.name);

    nock(BASE_URL)
      .put(`${TERMO_PATH}/${sanidade.id}/parent`)
      .reply(409, problemBody('termo-cycle', 409), PROBLEM_HEADERS);

    await submit(user, copy.moveSubmit);

    expect(
      await screen.findByText(copy.cycleUnderChild(concellos.name, sanidade.name)),
    ).toBeVisible();
  });

  it('refuses to delete a term with children with its own message, and leaves the tree alone', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia([...TAXONOMIA, innovacion]);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.delete));

    expect(await screen.findByText(copy.hasChildrenTitle)).toBeVisible();
    expect(screen.getByText(copy.hasChildrenOne(innovacion.name))).toBeVisible();
    expect(within(await dialog()).getByRole('button', { name: copy.deleteSubmit })).toBeDisabled();
    // The cycle message is the other 409; this one must not read as it.
    expect(screen.queryByText(copy.cycleTitle)).not.toBeInTheDocument();
    expect(nock.pendingMocks()).toEqual([]);
  });

  it('reports a duplicate sibling name on the field, keeping the typed name', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await screen.findByText(consellerias.name);
    await user.click(screen.getByRole('button', { name: copy.create }));
    await typeName(user, 'Concellos');

    nock(BASE_URL)
      .post(TERMOS_PATH)
      .reply(409, problemBody('duplicate-sibling-name', 409), PROBLEM_HEADERS);

    await submit(user, copy.createSubmit);

    expect(await screen.findByText(copy.duplicateSiblingName)).toBeVisible();
    // The dialog stays open with the name intact, so the administrator corrects
    // it rather than retyping it.
    expect(await nameField()).toHaveValue('Concellos');
  });

  it('reports a term another administrator deleted as a stale reference, not a generic failure', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await openTermo(user, sanidade.name);
    await user.click(paneAction(copy.rename));
    await typeName(user, 'Sanidade');

    nock(BASE_URL)
      .patch(`${TERMO_PATH}/${sanidade.id}`)
      .reply(404, problemBody('termo-not-found', 404), PROBLEM_HEADERS);

    await submit(user, copy.renameSubmit);

    expect(await screen.findByText(copy.notFound)).toBeVisible();
    expect(screen.queryByText(copy.genericError)).not.toBeInTheDocument();

    // The section recovers on the next read rather than staying broken.
    mockCatalogue(CATALOGUE);
    mockTaxonomia([consellerias, concellos]);
    await submit(user, copy.cancel);
    await openTermo(user, concellos.name);

    expect(await screen.findByRole('heading', { name: concellos.name })).toBeInTheDocument();
  });

  it('rejects a blank name in the dialog, without asking the server', async () => {
    const user = userEvent.setup();
    mockCatalogue(CATALOGUE);
    mockTaxonomia(TAXONOMIA);
    renderOrganosPage();

    await screen.findByText(consellerias.name);
    await user.click(screen.getByRole('button', { name: copy.create }));
    await typeName(user, '   ');
    await submit(user, copy.createSubmit);

    expect(await screen.findByText(copy.nameRequired)).toBeVisible();
    expect(nock.pendingMocks()).toEqual([]);
  });
});
