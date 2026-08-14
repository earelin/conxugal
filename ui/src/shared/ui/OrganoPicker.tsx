import {
  Box,
  getTreeExpandedState,
  Group,
  Popover,
  type RenderTreeNodePayload,
  Stack,
  Text,
  Tree,
  type TreeNodeData,
  UnstyledButton,
  useTree,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconChevronDown, IconChevronRight, IconSelector } from '@tabler/icons-react';
import { type KeyboardEvent, type MouseEvent, useId, useMemo } from 'react';
import { useMatch, useNavigate } from 'react-router';

import { strings } from '../lib/strings';
import {
  type Organo,
  pruneEmptyTermos,
  type TaxonomiaView,
  type TermoNode,
} from '../lib/taxonomiaTree';
import { ErrorAlert } from './ErrorAlert';
import { LoadingIndicator } from './LoadingIndicator';

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
      py={4}
      px={6}
      bg={selected ? 'indigo.0' : undefined}
      style={{ borderRadius: 'var(--mantine-radius-sm)' }}
    >
      <ExpandMarker hasChildren={hasChildren} expanded={expanded} />
      <Text size="sm" fw={termo ? 500 : 400} c={selected ? 'indigo.8' : undefined}>
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
    <Tree
      data={data}
      tree={tree}
      levelOffset="md"
      aria-labelledby={labelledBy}
      onKeyDown={actOnFocusedNodeOnEnter}
      renderNode={(payload) => <PickerRow payload={payload} onOpen={onOpen} />}
    />
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
  const [opened, { toggle, close }] = useDisclosure();
  const navigate = useNavigate();
  const labelId = useId();
  // Read from the route rather than held here, so the control keeps naming the
  // open Órgano on every tab of its page and after a reload.
  const openId = useMatch('/organo/:id/*')?.params.id ?? null;

  const { data, shape } = useMemo(() => {
    const nodes = view === null ? [] : toTreeData(pruneEmptyTermos(view.roots), view.unclassified);
    return { data: nodes, shape: treeValues(nodes).join() };
  }, [view]);

  function open(value: string) {
    const id = organoIdOf(value);
    if (id === null) {
      return;
    }
    // Choosing the Órgano already open closes the dropdown rather than pushing
    // the page the reader is on onto the history stack a second time.
    if (id !== openId) {
      void navigate(`/organo/${id}`);
    }
    close();
    onNavigate();
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
      <OrganoTree key={shape} data={data} openId={openId} onOpen={open} labelledBy={labelId} />
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
