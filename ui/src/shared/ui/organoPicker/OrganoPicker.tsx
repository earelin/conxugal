import { Group, Popover, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconSearch, IconSelector } from '@tabler/icons-react';
import { Activity, useId, useMemo, useRef, useState } from 'react';
import { useMatch, useNavigate } from 'react-router';

import { strings } from '../../lib/strings';
import { pruneEmptyTermos, type TaxonomiaView } from '../../lib/taxonomiaTree';
import { matches } from '../../lib/textSearch';
import { ErrorAlert } from '../ErrorAlert';
import { LoadingIndicator } from '../LoadingIndicator';
import { entersListFromSearch } from '../rovingFocus';
import { SEARCH_ICON_SIZE } from './dimensions';
import { OrganoMatches } from './OrganoMatches';
import { OrganoTree } from './OrganoTree';
import { organoIdOf, toTreeData, treeValues } from './treeData';

const copy = strings.organoPicker;

interface OrganoPickerProps {
  /** The visible set joined to the taxonomía; null until both reads land. */
  view: TaxonomiaView | null;
  isPending: boolean;
  isFetching: boolean;
  isError: boolean;
  onRetry: () => void;
  /** Closes the navbar the picker sits in, which on mobile overlays the page. */
  onNavigate: () => void;
}

/**
 * The reader's way into an Órgano, in the side panel of every page: a dropdown
 * over the Órganos they may browse, arranged by the taxonomía an administrator
 * filed them under. It offers no control that changes anything — it is a view,
 * not the administration tree with its buttons hidden.
 *
 * Browsing and searching are two states of this one control over one list, not
 * two components: with the filter empty the body is the tree, with text in it
 * the body is the matches. That is what makes the search offer exactly what the
 * tree shows, in both directions, rather than leaving two implementations to
 * stay in step.
 *
 * The reads happen above it. `shared/ui` may not reach `shared/entities`, and
 * the shell that renders the picker on every route is the layer that may.
 */
export function OrganoPicker({
  view,
  isPending,
  isFetching,
  isError,
  onRetry,
  onNavigate,
}: OrganoPickerProps) {
  const [query, setQuery] = useState('');
  // Emptied as the dropdown opens rather than as it closes. Every reader still
  // arrives on the tree, but the body renders throughout the closing fade, so
  // resetting on the way out would swap the matches for the whole tree and fade
  // *that* out in front of the reader who just picked one.
  const [opened, { toggle, close }] = useDisclosure(false, {
    onOpen: () => {
      setQuery('');
    },
  });
  const navigate = useNavigate();
  const labelId = useId();
  const listId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  // Read from the route rather than held here, so the control keeps naming the
  // open Órgano on every tab of its page and after a reload.
  const openId = useMatch('/organo/:id/*')?.params.id ?? null;

  const { data, shape } = useMemo(() => {
    const nodes = view === null ? [] : toTreeData(pruneEmptyTermos(view.roots), view.unclassified);
    return { data: nodes, shape: treeValues(nodes).join() };
  }, [view]);

  // A blank or whitespace-only filter has asked nothing, so it matches nothing
  // and the tree stands in for the answer.
  const trimmed = query.trim();
  const searching = trimmed !== '';
  // The catalogue, not the pruned tree and not a second read: filtering the
  // very list the tree was assembled from is what keeps the two in agreement.
  const found = useMemo(
    () =>
      view === null || trimmed === ''
        ? []
        : view.catalogue.filter((organo) => matches(organo.name, trimmed)),
    [view, trimmed],
  );
  const offersMatches = searching && found.length > 0;

  // The one way an Órgano is chosen, whichever state of the control offered it:
  // a tree row and a match lead to the same page because they call this.
  function chooseOrgano(id: string) {
    // Choosing the Órgano already open closes the dropdown rather than pushing
    // the page the reader is on onto the history stack a second time.
    if (id !== openId) {
      void navigate(`/organo/${id}`);
    }
    close();
    onNavigate();
  }

  function chooseRow(value: string) {
    const id = organoIdOf(value);
    if (id !== null) {
      chooseOrgano(id);
    }
  }

  const openOrgano = view?.catalogue.find((organo) => organo.id === openId) ?? null;
  // An id the visible set does not hold — a link shared before an Órgano's last
  // contract was withdrawn — reads as no selection rather than as a name the
  // picker cannot offer.
  const openName =
    openOrgano?.name ?? (openId !== null && isPending ? strings.loading : copy.placeholder);

  function body() {
    // A refetch that fails leaves the last good view in place: the reader is
    // mid-browse in a dropdown, and taking the tree away costs more than the
    // stale placement it would save. Only a failure with nothing to show is
    // reported, which is the state a fresh session meets.
    if (isError && view === null) {
      return (
        <ErrorAlert title={copy.errorTitle} onRetry={onRetry} retrying={isFetching}>
          {copy.errorHelp}
        </ErrorAlert>
      );
    }
    if (view === null) {
      return <LoadingIndicator />;
    }
    // Nothing to browse: every Órgano of the visible set is either unclassified
    // or inside a term the prune keeps, so an empty catalogue is an empty tree.
    if (view.catalogue.length === 0) {
      return (
        <Stack gap={4}>
          <Text size="sm">{copy.empty}</Text>
          <Text size="xs" c="dimmed">
            {copy.emptyHelp}
          </Text>
        </Stack>
      );
    }
    return (
      <Stack gap="xs">
        <TextInput
          value={query}
          onChange={(event) => {
            setQuery(event.currentTarget.value);
          }}
          onKeyDown={entersListFromSearch(listRef)}
          placeholder={copy.searchPlaceholder}
          aria-label={copy.searchLabel}
          // Names the list it narrows, which is the only thing tying the two
          // together: this is a search over a listbox, not a combobox owning
          // one. Only while that list is actually there — the tree state and
          // the refusal both hide it, and a reference to a hidden element is
          // one a screen reader cannot follow.
          aria-controls={offersMatches ? listId : undefined}
          leftSection={<IconSearch size={SEARCH_ICON_SIZE} aria-hidden />}
          size="sm"
        />
        {/* Hidden rather than unmounted while the filter has text in it: the
            tree opens every branch when it mounts, so swapping it out would
            cost a reader who collapsed one — and their scroll position with
            it — for the two keystrokes it took to check a name.

            `Activity` rather than `display: none`, which hides a tree without
            sparing it: every keystroke would otherwise reconcile a node per
            Órgano of the visible set, all of them off screen, because neither
            Mantine's `TreeNode` nor this tree is memoised. */}
        <Activity mode={searching ? 'hidden' : 'visible'}>
          <OrganoTree
            key={shape}
            data={data}
            openId={openId}
            onOpen={chooseRow}
            labelledBy={labelId}
          />
        </Activity>
        <OrganoMatches
          id={listId}
          organos={found}
          query={trimmed}
          openId={openId}
          onOpen={chooseOrgano}
          labelledBy={labelId}
          listRef={listRef}
        />
      </Stack>
    );
  }

  return (
    <Stack gap={4}>
      <Text id={labelId} size="xs" fw={700} c="dimmed" tt="uppercase" px="xs">
        {copy.label}
      </Text>
      <Popover
        opened={opened}
        onDismiss={close}
        // Without both, opening the dropdown leaves focus on the trigger: the
        // tree is then reachable only by tabbing through the rest of the page,
        // Escape is inert because Mantine listens for it inside the dropdown,
        // and the row that unmounts on selection drops focus onto the body.
        trapFocus
        returnFocus
        // Wide enough for a third-level Órgano name, narrow enough to stay
        // inside a 360 px viewport once the navbar's padding is spent.
        width={320}
        position="bottom-start"
        shadow="md"
        radius="md"
      >
        {/* The dropdown is a tree, a list of matches or a plain message
            depending on the filter, so it announces the container rather than
            any one of the three. */}
        <Popover.Target popupType="dialog">
          <UnstyledButton
            onClick={toggle}
            aria-labelledby={`${labelId} ${labelId}-value`}
            p="xs"
            style={{
              border: '1px solid var(--mantine-color-gray-3)',
              borderRadius: 'var(--mantine-radius-md)',
            }}
          >
            <Group gap="xs" wrap="nowrap" justify="space-between">
              <Text
                id={`${labelId}-value`}
                size="sm"
                fw={openOrgano ? 600 : 400}
                c={openOrgano ? undefined : 'dimmed'}
                lineClamp={2}
              >
                {openName}
              </Text>
              <IconSelector size={16} color="var(--mantine-color-gray-6)" aria-hidden />
            </Group>
          </UnstyledButton>
        </Popover.Target>
        <Popover.Dropdown p="sm">{body()}</Popover.Dropdown>
      </Popover>
    </Stack>
  );
}
