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
import { type KeyboardEvent, useId, useMemo } from 'react';
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
// opens its contracts.
const TERMO = 'termo:';
const ORGANO = 'organo:';

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

function ExpandMarker({ hasChildren, expanded }: { hasChildren: boolean; expanded: boolean }) {
  if (!hasChildren) {
    return <Box w={MARKER_SIZE} />;
  }
  const Chevron = expanded ? IconChevronDown : IconChevronRight;
  return <Chevron size={MARKER_SIZE} color="var(--mantine-color-gray-6)" aria-hidden />;
}

function PickerRow({ node, expanded, hasChildren, selected, elementProps }: RenderTreeNodePayload) {
  const isTermo = node.value.startsWith(TERMO);

  return (
    <Group
      {...elementProps}
      gap={6}
      wrap="nowrap"
      py={4}
      px={6}
      bg={selected ? 'indigo.0' : undefined}
      style={{ borderRadius: 'var(--mantine-radius-sm)' }}
    >
      <ExpandMarker hasChildren={hasChildren} expanded={expanded} />
      <Text size="sm" fw={isTermo ? 500 : 400} c={selected ? 'indigo.8' : undefined}>
        {node.label}
      </Text>
      {selected && <IconCheck size={MARKER_SIZE} aria-hidden />}
    </Group>
  );
}

interface OrganoTreeProps {
  data: TreeNodeData[];
  openId: string | null;
  onSelect: (value: string) => void;
  labelledBy: string;
}

/**
 * The tree itself, rendered only once there is something to draw. `useTree`
 * fixes its expanded state at the moment it is created and defaults unknown
 * nodes to collapsed, so a tree instance that outlived an empty `data` would
 * open every branch shut.
 */
function OrganoTree({ data, openId, onSelect, labelledBy }: OrganoTreeProps) {
  // A fresh array literal would bust useTree's own memo on every render.
  const selectedState = useMemo(() => (openId === null ? [] : [`${ORGANO}${openId}`]), [openId]);

  const tree = useTree({
    selectedState,
    initialExpandedState: getTreeExpandedState(data, '*'),
    onSelectedStateChange: (state) => {
      if (state.length > 0) {
        onSelect(state[0]);
      }
    },
  });

  // Mantine's own key handler covers the arrows and Space; there is no Enter
  // branch at all, so without this a keyboard user can walk the tree but never
  // open anything. The event bubbles from the focused `treeitem`, which carries
  // the node's value.
  function selectFocusedNodeOnEnter(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key !== 'Enter') {
      return;
    }
    const { value } = (event.target as HTMLElement).dataset;
    if (value !== undefined) {
      event.preventDefault();
      onSelect(value);
    }
  }

  return (
    <Tree
      data={data}
      tree={tree}
      levelOffset="md"
      selectOnClick
      allowRangeSelection={false}
      aria-labelledby={labelledBy}
      onKeyDown={selectFocusedNodeOnEnter}
      renderNode={(payload) => <PickerRow {...payload} />}
    />
  );
}

interface OrganoPickerProps {
  /** The visible set joined to the taxonomía; null while pending or failed. */
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

  const data = useMemo(
    () => (view === null ? [] : toTreeData(pruneEmptyTermos(view.roots), view.unclassified)),
    [view],
  );

  function select(value: string) {
    if (!value.startsWith(ORGANO)) {
      return;
    }
    void navigate(`/organo/${value.slice(ORGANO.length)}`);
    close();
    onNavigate();
  }

  const openOrgano = view?.catalogue.find((organo) => organo.id === openId) ?? null;

  function body() {
    if (isError) {
      return (
        <ErrorAlert title={copy.errorTitle} onRetry={onRetry} retrying={isFetching}>
          {copy.errorHelp}
        </ErrorAlert>
      );
    }
    if (isPending || view === null) {
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
    return <OrganoTree data={data} openId={openId} onSelect={select} labelledBy={labelId} />;
  }

  return (
    <Stack gap={4}>
      <Text id={labelId} size="xs" fw={700} c="dimmed" tt="uppercase" px="xs">
        {copy.label}
      </Text>
      <Popover
        opened={opened}
        onDismiss={close}
        // Wide enough for a third-level Órgano name, narrow enough to stay
        // inside a 360 px viewport once the navbar's padding is spent.
        width={320}
        position="bottom-start"
        shadow="md"
        radius="md"
      >
        <Popover.Target>
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
                {openOrgano?.name ?? copy.placeholder}
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
