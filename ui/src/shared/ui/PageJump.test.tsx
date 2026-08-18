import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { formatCount } from '../lib/number';
import { strings } from '../lib/strings';
import { PageJump } from './PageJump';

const copy = strings.pagination;

interface Options {
  page?: number;
  totalPages?: number;
  disabled?: boolean;
}

/** Page 3 of 37, so there is room either side of the page in force. */
function renderJump({ page = 3, totalPages = 37, disabled = false }: Options = {}) {
  const onPageChange = vi.fn();
  const view = render(
    <MantineProvider env="test">
      <PageJump
        page={page}
        totalPages={totalPages}
        disabled={disabled}
        onPageChange={onPageChange}
      />
    </MantineProvider>,
  );
  return { ...view, onPageChange, user: userEvent.setup() };
}

/** The box names itself with the visible word and the bound a page has to fall inside. */
function box(totalPages = 37) {
  return screen.getByRole('textbox', {
    name: `${copy.pageLabel} ${copy.ofPages(formatCount(totalPages))}`,
  });
}

describe('PageJump', () => {
  it('asks for the page typed into it when the reader presses Enter', async () => {
    const { onPageChange, user } = renderJump();

    await user.clear(box());
    await user.type(box(), '5{Enter}');

    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(5);
  });

  it('shows what the reader is typing before they commit it', async () => {
    const { onPageChange, user } = renderJump();

    await user.clear(box());
    await user.type(box(), '12');

    expect(box()).toHaveValue('12');
    expect(onPageChange).not.toHaveBeenCalled();
  });

  it('goes back to the page in force once a jump is committed', async () => {
    const { onPageChange, user, rerender } = renderJump();

    await user.clear(box());
    await user.type(box(), '5{Enter}');
    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(5);

    // The caller answers the jump by handing back the page it moved to.
    rerender(
      <MantineProvider env="test">
        <PageJump page={5} totalPages={37} disabled={false} onPageChange={onPageChange} />
      </MantineProvider>,
    );

    expect(box()).toHaveValue('5');
  });

  it.each([
    ['0', 'a page before the first'],
    ['38', 'a page past the last'],
    ['abc', 'something that is not a number'],
    ['5.5', 'a page between two pages'],
    ['0x10', 'a page written in another base'],
    ['1e1', 'a page written as an exponent'],
  ])('refuses «%s» — %s — and shows the refusal by reverting', async (asked) => {
    const { onPageChange, user } = renderJump({ page: 3 });

    await user.clear(box());
    await user.type(box(), `${asked}{Enter}`);

    expect(onPageChange).not.toHaveBeenCalled();
    expect(box()).toHaveValue('3');
  });

  it('asks for nothing when Enter is pressed on a box nobody has touched', async () => {
    const { onPageChange, user } = renderJump();

    await user.click(box());
    await user.keyboard('{Enter}');

    expect(onPageChange).not.toHaveBeenCalled();
    expect(box()).toHaveValue('3');
  });

  it('drops what was typed when focus leaves, so tabbing away never pages', async () => {
    const { onPageChange, user } = renderJump({ page: 3 });

    await user.clear(box());
    await user.type(box(), '9');
    await user.tab();

    expect(onPageChange).not.toHaveBeenCalled();
    expect(box()).toHaveValue('3');
  });

  it('follows the page it is given while the reader is not typing', () => {
    const onPageChange = vi.fn();
    const { rerender } = render(
      <MantineProvider env="test">
        <PageJump page={3} totalPages={37} disabled={false} onPageChange={onPageChange} />
      </MantineProvider>,
    );
    expect(box()).toHaveValue('3');

    rerender(
      <MantineProvider env="test">
        <PageJump page={4} totalPages={37} disabled={false} onPageChange={onPageChange} />
      </MantineProvider>,
    );

    expect(box()).toHaveValue('4');
  });

  it('keeps Enter to itself, so a jump inside a form does not submit it', async () => {
    const onPageChange = vi.fn();
    // Typed by what the handler uses rather than by React's event alias, which
    // is deprecated: the test only needs to stop jsdom from acting on a submit.
    const onSubmit = vi.fn((event: { preventDefault: () => void }) => {
      event.preventDefault();
    });
    render(
      <MantineProvider env="test">
        <form onSubmit={onSubmit}>
          <PageJump page={3} totalPages={37} disabled={false} onPageChange={onPageChange} />
        </form>
      </MantineProvider>,
    );
    const user = userEvent.setup();

    await user.clear(box());
    await user.type(box(), '5{Enter}');

    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(5);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('takes no page at all while it is disabled', async () => {
    const { onPageChange, user } = renderJump({ disabled: true });

    expect(box()).toBeDisabled();
    await user.type(box(), '5{Enter}');

    expect(onPageChange).not.toHaveBeenCalled();
    expect(box()).toHaveValue('3');
  });

  it('states the bound a page has to fall inside, grouped as every count is', () => {
    renderJump({ page: 1, totalPages: 1234 });

    expect(screen.getByText(copy.ofPages('1 234'))).toBeInTheDocument();
    // The bound is part of what the box calls itself, so it is stated at the
    // moment a page is being typed rather than only somewhere alongside.
    expect(box(1234)).toBeInTheDocument();
  });

  it('focuses the box when the reader clicks the word that labels it', async () => {
    const { user } = renderJump();

    await user.click(screen.getByText(copy.pageLabel));

    expect(box()).toHaveFocus();
  });
});
