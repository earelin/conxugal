import { Box, Group, Input, ScrollArea, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconCheck, IconSearch } from '@tabler/icons-react';
import { type KeyboardEvent, useId, useMemo, useRef, useState } from 'react';

import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';
import { buildTermoPickerRows } from './termoSearch';

const copy = strings.admin.organos.assign;

const OPTION_SELECTOR = '[role="option"]';

// One indent step per level, and the height of the panel the rows scroll in.
// Both come from the mockup.
const INDENT_STEP = 20;
const BASE_INDENT = 12;
const PANEL_HEIGHT = 288;

/** Where a key moves focus within the list, or null when it is not ours. */
function nextIndex(key: string, current: number, last: number): number | null {
  switch (key) {
    case 'ArrowDown':
      return Math.min(current + 1, last);
    case 'ArrowUp':
      return Math.max(current - 1, 0);
    case 'Home':
      return 0;
    case 'End':
      return last;
    default:
      return null;
  }
}

function optionsOf(list: HTMLElement | null): HTMLElement[] {
  return list === null ? [] : Array.from(list.querySelectorAll<HTMLElement>(OPTION_SELECTOR));
}

export interface TermoTreePickerProps {
  roots: TermoNode[];
  /** The field's label; the list of terms takes its accessible name from it. */
  label: string;
  /** The chosen term, or null while nothing has been picked. */
  value: string | null;
  /**
   * Only ever an id. The picker offers no "no term" row: taking an Órgano out of
   * the taxonomía is `Quitar do termo`, a different action on a different screen.
   */
  onChange: (termoId: string) => void;
  required?: boolean;
}

/**
 * The taxonomía as a searchable, indented list of destinations.
 *
 * Deliberately not Mantine's `Tree`: a picker has nothing to expand or collapse,
 * and `useTree` owns an expanded state keyed by node — which every keystroke of
 * the search would have to reconcile as the set of visible nodes changes. Rows
 * derived from `(roots, query, value)` have no such state, and being real
 * buttons they answer Enter and Space without the hand-written key handling the
 * section's other tree needed.
 */
export function TermoTreePicker({ roots, label, value, onChange, required }: TermoTreePickerProps) {
  const labelId = useId();
  const listId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState('');

  const rows = useMemo(() => buildTermoPickerRows(roots, query, value), [roots, query, value]);

  // The list is one tab stop, and which row it lands on is derived rather than
  // held: a focus index in state would need re-syncing every time the filter
  // changed the rows under it.
  const chosenRow = value === null ? undefined : rows.find((row) => row.id === value);
  const tabbableId = chosenRow?.id ?? rows[0]?.id;

  function moveFocus(event: KeyboardEvent<HTMLDivElement>) {
    const options = optionsOf(listRef.current);
    if (options.length === 0) {
      return;
    }
    const current = options.indexOf(document.activeElement as HTMLElement);
    const target = nextIndex(event.key, current, options.length - 1);
    if (target === null) {
      return;
    }
    event.preventDefault();
    options[target].focus();
  }

  function enterListFromSearch(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'ArrowDown') {
      return;
    }
    const [first] = optionsOf(listRef.current);
    if (first) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <Input.Wrapper
      label={label}
      required={required}
      // There is no single control for a `label for` to point at, and a dangling
      // `htmlFor` would be worse than none: the list borrows the label by id.
      labelElement="div"
      labelProps={{ id: labelId }}
    >
      <TextInput
        mt={4}
        aria-label={copy.searchLabel}
        // Names the list it narrows, which is the only thing tying the two
        // together: this is a search over a listbox, not a combobox that owns it.
        aria-controls={listId}
        placeholder={copy.searchPlaceholder}
        leftSection={<IconSearch size={16} aria-hidden />}
        value={query}
        onChange={(event) => setQuery(event.currentTarget.value)}
        onKeyDown={enterListFromSearch}
      />
      <Box mt="xs" bd="1px solid gray.3" bdrs="md">
        <ScrollArea.Autosize mah={PANEL_HEIGHT} type="auto">
          <Box
            ref={listRef}
            id={listId}
            role="listbox"
            aria-labelledby={labelId}
            // The asterisk Input.Wrapper draws is aria-hidden, so without this
            // the field's required-ness would be sighted-only.
            aria-required={required}
            tabIndex={-1}
            onKeyDown={moveFocus}
            p={4}
          >
            {rows.map((row) => {
              const selected = row.id === value;
              return (
                <UnstyledButton
                  key={row.id}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  tabIndex={row.id === tabbableId ? 0 : -1}
                  onClick={() => onChange(row.id)}
                  display="block"
                  w="100%"
                  py={6}
                  pr="xs"
                  pl={BASE_INDENT + row.depth * INDENT_STEP}
                  bg={selected ? 'indigo.0' : undefined}
                  style={{ borderRadius: 'var(--mantine-radius-sm)' }}
                >
                  <Group gap="xs" justify="space-between" wrap="nowrap">
                    <Text
                      size="sm"
                      fw={selected || row.depth === 0 ? 600 : 400}
                      c={selected ? 'indigo.8' : undefined}
                    >
                      {row.name}
                    </Text>
                    {/* A Tabler icon paints its own stroke, so this is a CSS
                        value rather than a Mantine colour token. */}
                    {selected && (
                      <IconCheck size={14} color="var(--mantine-color-indigo-8)" aria-hidden />
                    )}
                  </Group>
                </UnstyledButton>
              );
            })}
          </Box>
          {/* Always mounted, so a screen reader hears the list empty out under
              a query rather than nothing at all. An empty taxonomía is not a
              failed search — it is the normal state before the first term is
              created, and it says so. */}
          <Box role="status">
            {rows.length === 0 && (
              <Text c="dimmed" size="sm" p="sm">
                {roots.length === 0
                  ? strings.admin.organos.treeEmpty
                  : copy.noTermoMatches(query.trim())}
              </Text>
            )}
          </Box>
        </ScrollArea.Autosize>
      </Box>
    </Input.Wrapper>
  );
}
