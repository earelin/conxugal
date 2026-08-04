import { MantineProvider } from '@mantine/core';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { strings } from '../../shared/lib/strings';
import type { Organo, Termo } from './organos';
import { buildTaxonomiaView } from './taxonomiaTree';
import { TermoTreePicker } from './TermoTreePicker';

const copy = strings.admin.organos.assign;

const TAXONOMIA: Termo[] = [
  { id: 't-1', name: 'Consellerías', parentId: null },
  { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' },
  { id: 't-3', name: 'Consellería de Educación', parentId: 't-1' },
  { id: 't-4', name: 'Axencia Galega de Innovación', parentId: 't-3' },
  { id: 't-5', name: 'Concellos', parentId: null },
];

const NO_ORGANOS: Organo[] = [];

// Tree order, which is the order the rows are drawn and walked in.
const ROW_NAMES = [
  'Consellerías',
  'Consellería de Sanidade',
  'Consellería de Educación',
  'Axencia Galega de Innovación',
  'Concellos',
];

/** Controlled the way the dialog controls it, so a pick really moves the value. */
function Harness({ initial, termos }: { initial: string | null; termos: Termo[] }) {
  const [value, setValue] = useState(initial);
  const view = buildTaxonomiaView(termos, NO_ORGANOS);
  return (
    <TermoTreePicker
      roots={view.roots}
      label={copy.termoLabel}
      required
      value={value}
      onChange={setValue}
    />
  );
}

function renderPicker(initial: string | null = null, termos: Termo[] = TAXONOMIA) {
  return render(
    <MantineProvider theme={theme}>
      <Harness initial={initial} termos={termos} />
    </MantineProvider>,
  );
}

const list = () => screen.getByRole('listbox', { name: copy.termoLabel });
const search = () => screen.getByRole('textbox', { name: copy.searchLabel });
const optionFor = (name: string) => within(list()).getByRole('option', { name });
const optionNames = () =>
  within(list())
    .queryAllByRole('option')
    .map((option) => option.textContent);

/** The single row the list offers Tab, which is what a roving tab stop means. */
function tabStop(): HTMLElement {
  const stops = within(list())
    .getAllByRole('option')
    .filter((option) => option.getAttribute('tabindex') === '0');
  expect(stops).toHaveLength(1);
  return stops[0];
}

async function search_(user: UserEvent, query: string) {
  await user.clear(search());
  await user.type(search(), query);
}

describe('TermoTreePicker keyboard navigation', () => {
  it('moves focus off the search box and into the list on ArrowDown', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(search());
    await user.keyboard('{ArrowDown}');

    expect(optionFor('Consellerías')).toHaveFocus();
  });

  it('leaves the search box alone for a key that is not ArrowDown', async () => {
    const user = userEvent.setup();
    renderPicker();

    await user.click(search());
    await user.keyboard('{ArrowUp}');

    expect(search()).toHaveFocus();
  });

  it('walks down the rows and stops at the last one rather than wrapping', async () => {
    const user = userEvent.setup();
    renderPicker();

    optionFor(ROW_NAMES[0]).focus();
    // One press more than there are rows: the clamp is the assertion.
    await user.keyboard('{ArrowDown}'.repeat(ROW_NAMES.length + 1));

    expect(optionFor(ROW_NAMES[ROW_NAMES.length - 1])).toHaveFocus();
  });

  it('walks back up the rows and stops at the first one', async () => {
    const user = userEvent.setup();
    renderPicker();

    optionFor(ROW_NAMES[2]).focus();
    await user.keyboard('{ArrowUp}{ArrowUp}{ArrowUp}{ArrowUp}');

    expect(optionFor(ROW_NAMES[0])).toHaveFocus();
  });

  it('jumps to the last row with End and to the first with Home', async () => {
    const user = userEvent.setup();
    renderPicker();

    optionFor(ROW_NAMES[1]).focus();
    await user.keyboard('{End}');
    expect(optionFor(ROW_NAMES[ROW_NAMES.length - 1])).toHaveFocus();

    await user.keyboard('{Home}');
    expect(optionFor(ROW_NAMES[0])).toHaveFocus();
  });

  it('picks the focused row with Enter, because the rows are real buttons', async () => {
    const user = userEvent.setup();
    renderPicker();

    optionFor(ROW_NAMES[0]).focus();
    await user.keyboard('{ArrowDown}{Enter}');

    expect(optionFor('Consellería de Sanidade')).toHaveAttribute('aria-selected', 'true');
    expect(within(list()).getAllByRole('option', { selected: true })).toHaveLength(1);
  });

  it('picks the focused row with Space as well', async () => {
    const user = userEvent.setup();
    renderPicker();

    optionFor('Concellos').focus();
    await user.keyboard(' ');

    expect(optionFor('Concellos')).toHaveAttribute('aria-selected', 'true');
  });

  it('does nothing when a navigation key arrives at a list the filter has emptied', async () => {
    const user = userEvent.setup();
    renderPicker();

    await search_(user, 'zzz');
    await waitFor(() => {
      expect(optionNames()).toEqual([]);
    });

    // The panel is focusable, so this is reachable: click the empty box and
    // press an arrow. Without the guard the handler indexes an empty array.
    await user.click(list());
    await user.keyboard('{ArrowDown}{End}{Home}');

    expect(optionNames()).toEqual([]);
  });
});

describe('TermoTreePicker tab stop', () => {
  it('offers one tab stop, on the first row, while nothing is chosen', () => {
    renderPicker();

    expect(tabStop()).toHaveAccessibleName(ROW_NAMES[0]);
  });

  it('moves its tab stop onto the chosen row', () => {
    renderPicker('t-5');

    expect(tabStop()).toHaveAccessibleName('Concellos');
  });

  it('keeps its tab stop on the chosen row however narrow the query gets', async () => {
    const user = userEvent.setup();
    renderPicker('t-5');

    await search_(user, 'sanidade');
    await waitFor(() => {
      expect(optionNames()).toContain('Consellería de Sanidade');
    });

    // The chosen term is never filtered away, so the tab stop has no reason to
    // move — narrowing the question must not silently move the answer.
    expect(optionNames()).toContain('Concellos');
    expect(tabStop()).toHaveAccessibleName('Concellos');
  });

  it('falls back to the first row when the chosen term is not in the taxonomía', () => {
    // A term another administrator deleted: nothing pins it, so nothing shows
    // it, and the tab stop has to land somewhere real.
    renderPicker('t-deleted');

    expect(within(list()).queryAllByRole('option', { selected: true })).toEqual([]);
    expect(tabStop()).toHaveAccessibleName(ROW_NAMES[0]);
  });
});

describe('TermoTreePicker accessibility', () => {
  it('names the list of terms after the field it belongs to', () => {
    renderPicker();

    expect(list()).toHaveAccessibleName(copy.termoLabel);
  });

  it('states the field is required where a screen reader can reach it', () => {
    renderPicker();

    // The asterisk Input.Wrapper draws is aria-hidden, so it cannot carry this.
    expect(list()).toHaveAttribute('aria-required', 'true');
  });

  it('ties the search box to the list it narrows', () => {
    renderPicker();

    expect(search()).toHaveAttribute('aria-controls', list().id);
  });

  it('marks every row selected or not, never leaving the state unsaid', () => {
    renderPicker('t-2');

    for (const option of within(list()).getAllByRole('option')) {
      expect(option).toHaveAttribute('aria-selected');
    }
    expect(within(list()).getAllByRole('option', { selected: true })).toHaveLength(1);
  });

  it('announces an empty result in a live region rather than silently', async () => {
    const user = userEvent.setup();
    renderPicker();

    await search_(user, 'zzz');

    const status = await screen.findByRole('status');

    expect(status).toHaveTextContent(copy.noTermoMatches('zzz'));
  });

  it('keeps the live region mounted while there are results to lose', () => {
    renderPicker();

    // Mounted before the text changes, or the change is never announced.
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });

  it('calls an empty taxonomía empty rather than a failed search', () => {
    renderPicker(null, []);

    expect(screen.getByRole('status')).toHaveTextContent(strings.admin.organos.treeEmpty);
  });
});
