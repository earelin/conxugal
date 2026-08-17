import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { counted } from '../lib/plural';
import { strings } from '../lib/strings';
import { Pagination } from './Pagination';

const copy = strings.pagination;

interface Envelope {
  page?: number;
  size?: number;
  totalItems?: number;
  totalPages?: number;
}

/** The middle of a 37-page selection, so every control is live unless a case says otherwise. */
function renderPagination({
  page = 3,
  size = 50,
  totalItems = 1832,
  totalPages = 37,
}: Envelope = {}) {
  const onPageChange = vi.fn();
  const view = render(
    <MantineProvider env="test">
      <Pagination
        page={page}
        size={size}
        totalItems={totalItems}
        totalPages={totalPages}
        onPageChange={onPageChange}
      />
    </MantineProvider>,
  );
  return { ...view, onPageChange, user: userEvent.setup() };
}

function button(name: string) {
  return screen.getByRole('button', { name });
}

/**
 * The box names itself with the visible word *and* the page total, so a reader
 * typing into it is told the range they have to stay inside.
 */
function jumpBox(totalPages = 37) {
  return screen.getByRole('textbox', {
    name: `${copy.pageLabel} ${copy.ofPages(totalPages)}`,
  });
}

describe('Pagination', () => {
  it('offers the five controls, and states both the entry count and the page total', () => {
    renderPagination();

    expect(button(copy.first)).toBeInTheDocument();
    expect(button(copy.previous)).toBeInTheDocument();
    expect(button(copy.next)).toBeInTheDocument();
    expect(button(copy.last)).toBeInTheDocument();
    expect(jumpBox()).toHaveValue('3');
    expect(screen.getByText(counted(1832, copy.records))).toBeInTheDocument();
    expect(screen.getByText(copy.ofPages(37))).toBeInTheDocument();
  });

  it('is a navigation landmark of its own, so a reader can be taken straight to it', () => {
    renderPagination();

    expect(screen.getByRole('navigation', { name: copy.navLabel })).toBeInTheDocument();
  });

  it('disables the two backward controls on the first page and still draws them', () => {
    renderPagination({ page: 1 });

    expect(button(copy.first)).toBeDisabled();
    expect(button(copy.previous)).toBeDisabled();
    expect(button(copy.next)).toBeEnabled();
    expect(button(copy.last)).toBeEnabled();
  });

  it('disables the two forward controls on the last page and still draws them', () => {
    renderPagination({ page: 37 });

    expect(button(copy.first)).toBeEnabled();
    expect(button(copy.previous)).toBeEnabled();
    expect(button(copy.next)).toBeDisabled();
    expect(button(copy.last)).toBeDisabled();
  });

  it('still states the count over a single page, with every control disabled', () => {
    renderPagination({ page: 1, totalItems: 18, totalPages: 1 });

    expect(button(copy.first)).toBeDisabled();
    expect(button(copy.previous)).toBeDisabled();
    expect(button(copy.next)).toBeDisabled();
    expect(button(copy.last)).toBeDisabled();
    expect(jumpBox(1)).toBeDisabled();
    // The point of the case: how many entries there are is an answer in its own
    // right, not a by-product of there being somewhere to page to.
    expect(screen.getByText(counted(18, copy.records))).toBeInTheDocument();
    expect(screen.getByText(copy.ofPages(1))).toBeInTheDocument();
  });

  it('says one rexistro in the singular and the rest in the plural', () => {
    renderPagination({ totalItems: 1, totalPages: 1 });
    expect(screen.getByText(`1 ${copy.records.singular}`)).toBeInTheDocument();

    renderPagination({ totalItems: 2, totalPages: 1 });
    expect(screen.getByText(`2 ${copy.records.plural}`)).toBeInTheDocument();
  });

  it('draws an empty selection without a page to offer, rather than failing', () => {
    renderPagination({ page: 1, totalItems: 0, totalPages: 0 });

    expect(screen.getByText(counted(0, copy.records))).toBeInTheDocument();
    expect(screen.getByText(copy.ofPages(0))).toBeInTheDocument();
    expect(button(copy.first)).toBeDisabled();
    expect(button(copy.last)).toBeDisabled();
    expect(jumpBox(0)).toBeDisabled();
  });

  it('emits the page each control names, in the same 1-based number it displays', async () => {
    const { onPageChange, user } = renderPagination({ page: 3, totalPages: 37 });

    await user.click(button(copy.first));
    expect(onPageChange).toHaveBeenLastCalledWith(1);

    await user.click(button(copy.previous));
    expect(onPageChange).toHaveBeenLastCalledWith(2);

    await user.click(button(copy.next));
    expect(onPageChange).toHaveBeenLastCalledWith(4);

    // Named, not counted: the last page is the one a reader asks for by name.
    await user.click(button(copy.last));
    expect(onPageChange).toHaveBeenLastCalledWith(37);
  });

  it('jumps to a page inside the selection when the reader presses Enter', async () => {
    const { onPageChange, user } = renderPagination();

    await user.clear(jumpBox());
    await user.type(jumpBox(), '5{Enter}');

    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(5);
  });

  it.each([
    ['0', 'a page before the first'],
    ['38', 'a page past the last'],
    ['abc', 'something that is not a number'],
    ['', 'nothing at all'],
  ])('refuses «%s» — %s — rather than emitting it', async (asked) => {
    const { onPageChange, user } = renderPagination({ page: 3, totalPages: 37 });

    await user.clear(jumpBox());
    await user.type(jumpBox(), `${asked}{Enter}`);

    expect(onPageChange).not.toHaveBeenCalled();
    // The refusal is what the box shows: it goes back to the page in force.
    expect(jumpBox()).toHaveValue('3');
  });

  it('drops an uncommitted number when focus leaves, so tabbing away never pages', async () => {
    const { onPageChange, user } = renderPagination({ page: 3 });

    await user.clear(jumpBox());
    await user.type(jumpBox(), '9');
    await user.tab();

    expect(onPageChange).not.toHaveBeenCalled();
    expect(jumpBox()).toHaveValue('3');
  });

  it('follows the page it is given, so paging by button keeps the box honest', () => {
    const { rerender } = renderPagination({ page: 3 });
    expect(jumpBox()).toHaveValue('3');

    rerender(
      <MantineProvider env="test">
        <Pagination page={4} size={50} totalItems={1832} totalPages={37} onPageChange={vi.fn()} />
      </MantineProvider>,
    );

    expect(jumpBox()).toHaveValue('4');
  });
});
