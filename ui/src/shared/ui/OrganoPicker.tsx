import {
  Badge,
  Box,
  getTreeExpandedState,
  Group,
  Popover,
  type RenderTreeNodePayload,
  ScrollArea,
  Stack,
  Text,
  TextInput,
  Tree,
  type TreeNodeData,
  UnstyledButton,
  useTree,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconCheck,
  IconChevronDown,
  IconChevronRight,
  IconSearch,
  IconSelector,
} from '@tabler/icons-react';
import { type KeyboardEvent, type MouseEvent, useId, useMemo, useState } from 'react';
import { useMatch, useNavigate } from 'react-router';

import { matches } from '../lib/organoSearch';
import { strings } from '../lib/strings';
import {
  type Organo,
  pruneEmptyTermos,
  type TaxonomiaView,
  type TermoNode,
} from '../lib/taxonomiaTree';
import { ErrorAlert } from './ErrorAlert';
import { LoadingIndicator } from './LoadingIndicator';
import { SELECTED_ROW_BG, SELECTED_ROW_COLOR } from './selection';

const copy = strings.organoPicker;

// Term ids and Órgano ids come from different tables, so a row's value says
// which of the two it is: a term row only opens its branch, an Órgano row
// opens its contracts. Every reader of that distinction goes through the two
// functions below rather than slicing the prefix itself.
const TERMO = 'termo:';
const ORGANO = 'organo:';

function isTermo(value: string): boolean {
  return value.startsWith(TERMO);
}

/** The Órgano a row stands for, or null when the row is a term. */
function organoIdOf(value: string): string | null {
  return value.startsWith(ORGANO) ? value.slice(ORGANO.length) : null;
}

// Chevron and leaf spacer share this width, which lines labels up across a
// level whether or not the row has children.
const MARKER_SIZE = 14;

// Relative to the window rather than a pixel count, so the tree still fits
// under the trigger on a short viewport.
const MAX_TREE_HEIGHT = '60vh';

function organoNodes(organos: Organo[]): TreeNodeData[] {
  return organos.map((organo) => ({ value: `${ORGANO}${organo.id}`, label: organo.name }));
}

/**
 * Child terms first, then the term's own Órganos — at the root too, which is
 * what puts the unclassified Órganos beside the root terms rather than under a
 * heading of their own. Nothing sorts: both reads arrive in name order.
 */
function toTreeData(nodes: TermoNode[], organos: Organo[]): TreeNodeData[] {
  const termos = nodes.map((node) => ({
    value: `${TERMO}${node.id}`,
    label: node.name,
    children: toTreeData(node.children, node.organos),
  }));
  return [...termos, ...organoNodes(organos)];
}

/** Every value in the tree, which is what changes when the taxonomía does. */
function treeValues(nodes: TreeNodeData[]): string[] {
  return nodes.flatMap((node) => [node.value, ...treeValues(node.children ?? [])]);
}

function ExpandMarker({ hasChildren, expanded }: { hasChildren: boolean; expanded: boolean }) {
  if (!hasChildren) {
    return <Box w={MARKER_SIZE} />;
  }
  const Chevron = expanded ? IconChevronDown : IconChevronRight;
  return <Chevron size={MARKER_SIZE} color="var(--mantine-color-gray-6)" aria-hidden />;
}

interface PickerRowProps {
  payload: RenderTreeNodePayload;
  onOpen: (value: string) => void;
}

function PickerRow({ payload, onOpen }: PickerRowProps) {
  const { node, expanded, hasChildren, selected, elementProps } = payload;
  const termo = isTermo(node.value);

  return (
    <Group
      {...elementProps}
      // Mantine's own handler expands the branch; opening the Órgano is ours.
      onClick={(event: MouseEvent<HTMLDivElement>) => {
        elementProps.onClick(event);
        onOpen(node.value);
      }}
      gap={6}
      wrap="nowrap"
      // Tall enough to read, short enough that its middle meets the elbow of
      // the guide line, which Mantine pins at a fixed 12 px down the row.
      py={2}
      // Only the end side: the row's start padding is the indent, which
      // Mantine's own label class takes from the level it set on the `li`. Any
      // padding prop covering the start side — `px`, `p`, `pl`, `ps` — writes
      // over it and flattens the tree into a single column, and the guide
      // lines are drawn expecting a row to begin exactly at that offset.
      pe={6}
      bg={selected ? SELECTED_ROW_BG : undefined}
      // On the row rather than the label, so the check inherits it too — the
      // chevron sets its own colour and is unaffected.
      c={selected ? SELECTED_ROW_COLOR : undefined}
      style={{ ...elementProps.style, borderRadius: 'var(--mantine-radius-sm)' }}
    >
      <ExpandMarker hasChildren={hasChildren} expanded={expanded} />
      <Text size="sm" fw={termo ? 500 : 400}>
        {node.label}
      </Text>
      {selected && <IconCheck size={MARKER_SIZE} aria-hidden />}
    </Group>
  );
}

interface OrganoTreeProps {
  data: TreeNodeData[];
  /** The Órgano the route has open, marked as selected. */
  openId: string | null;
  onOpen: (value: string) => void;
  labelledBy: string;
}

/**
 * The tree itself, rendered only once there is something to draw and remounted
 * whenever the taxonomía changes shape: `useTree` fixes its expanded state when
 * it is created and defaults nodes it has not seen to collapsed, so a longer
 * lived instance would show a term that arrived later shut inside an otherwise
 * open tree.
 *
 * Selection here is presentational — the route says which Órgano is open, and
 * nothing in the tree writes back to it. Opening one runs off the row's own
 * click and the Enter key instead, because Mantine's keyboard handler applies
 * range selection whether or not `allowRangeSelection` is set, and a range
 * would otherwise navigate to whichever Órgano happened to head it.
 */
function OrganoTree({ data, openId, onOpen, labelledBy }: OrganoTreeProps) {
  const selectedState = useMemo(() => (openId === null ? [] : [`${ORGANO}${openId}`]), [openId]);
  const initialExpandedState = useMemo(() => getTreeExpandedState(data, '*'), [data]);

  const tree = useTree({ selectedState, initialExpandedState });

  // Mantine's own key handler covers the arrows and Space; there is no Enter
  // branch at all, so without this a keyboard user can walk the tree but never
  // act on it. The event bubbles from the focused `treeitem`, which carries the
  // node's value.
  function actOnFocusedNodeOnEnter(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key !== 'Enter') {
      return;
    }
    const { value } = (event.target as HTMLElement).dataset;
    if (value === undefined) {
      return;
    }
    event.preventDefault();
    if (isTermo(value)) {
      tree.toggleExpanded(value);
    } else {
      onOpen(value);
    }
  }

  return (
    // Every branch opens at once, so a large visible set draws a tree taller
    // than the window it drops into.
    <ScrollArea.Autosize mah={MAX_TREE_HEIGHT} type="auto">
      <Tree
        data={data}
        tree={tree}
        levelOffset="md"
        // The indent alone is a thin signal in a narrow dropdown holding names
        // long enough to wrap; the lines say which branch a row hangs off even
        // when its label runs over two rows.
        withLines
        // Inset the whole tree rather than each row: a row's own start padding
        // is its indent, and the guide lines are positioned within the row, so
        // both shift together and stay aligned with the chevron above them.
        ps={6}
        aria-labelledby={labelledBy}
        onKeyDown={actOnFocusedNodeOnEnter}
        renderNode={(payload) => <PickerRow payload={payload} onOpen={onOpen} />}
      />
    </ScrollArea.Autosize>
  );
}

interface OrganoMatchesProps {
  organos: Organo[];
  /** What was typed, quoted back when nothing matches it. */
  query: string;
  onOpen: (id: string) => void;
  labelledBy: string;
}

/**
 * The filter's answer: a flat list of the Órganos whose names hold the query,
 * or the refusal when none do. It is the same control the tree is, in its other
 * state — so it offers no paging, no sorting and no count, and it never falls
 * back to listing the catalogue when it has nothing to say.
 */
function OrganoMatches({ organos, query, onOpen, labelledBy }: OrganoMatchesProps) {
  if (organos.length === 0) {
    return (
      <Stack gap={4}>
        <Text size="sm">{copy.noMatches(query)}</Text>
        <Text size="xs" c="dimmed">
          {copy.noMatchesHelp}
        </Text>
      </Stack>
    );
  }

  return (
    <Stack
      component="ul"
      gap={2}
      aria-labelledby={labelledBy}
      style={{ listStyle: 'none', margin: 0, padding: 0 }}
    >
      {organos.map((organo) => (
        <Box component="li" key={organo.id}>
          <UnstyledButton
            onClick={() => {
              onOpen(organo.id);
            }}
            w="100%"
            py={4}
            px={6}
            style={{ borderRadius: 'var(--mantine-radius-sm)' }}
          >
            <Group gap="xs" wrap="nowrap" justify="space-between">
              <Text size="sm" c={organo.active ? undefined : 'dimmed'}>
                {organo.name}
              </Text>
              {/* Reading dimmer says nothing to a screen reader, so an inactive
                  Órgano says so in its text as well. It stays selectable:
                  inactive is a fact about the catalogue, not about whether
                  there is anything to see. */}
              {!organo.active && (
                <Badge color="gray" variant="light" size="xs">
                  {copy.inactive}
                </Badge>
              )}
            </Group>
          </UnstyledButton>
        </Box>
      ))}
    </Stack>
  );
}

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
  // Closing empties the filter: a dropdown reopened still narrowed by a query
  // the reader has forgotten typing reads as a catalogue missing most of itself.
  const [opened, { toggle, close }] = useDisclosure(false, {
    onClose: () => {
      setQuery('');
    },
  });
  const navigate = useNavigate();
  const labelId = useId();
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
  // The catalogue, not the pruned tree and not a second read: filtering the
  // very list the tree was assembled from is what keeps the two in agreement.
  const found = useMemo(
    () =>
      view === null || trimmed === ''
        ? []
        : view.catalogue.filter((organo) => matches(organo.name, trimmed)),
    [view, trimmed],
  );

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
          placeholder={copy.searchPlaceholder}
          aria-label={copy.searchPlaceholder}
          leftSection={<IconSearch size={MARKER_SIZE} aria-hidden />}
          size="sm"
        />
        {trimmed === '' ? (
          <OrganoTree
            key={shape}
            data={data}
            openId={openId}
            onOpen={chooseRow}
            labelledBy={labelId}
          />
        ) : (
          <OrganoMatches
            organos={found}
            query={trimmed}
            onOpen={chooseOrgano}
            labelledBy={labelId}
          />
        )}
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
        <Popover.Target popupType="tree">
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
