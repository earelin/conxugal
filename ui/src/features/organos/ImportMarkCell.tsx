import { Badge, Flex, type MantineColor, Switch, Text, Tooltip } from '@mantine/core';

import { strings } from '../../shared/lib/strings';
import { UNTRUNCATED_LABEL } from '../../shared/ui/badge';
import { markState, type MarkState, markTooltip } from './importMark';
import type { Organo } from './organos';

const copy = strings.admin.organos.contratosMenores;

const BADGE: Partial<Record<MarkState, { label: string; color: MantineColor }>> = {
  marked: { label: copy.badge.marked, color: 'indigo' },
  partial: { label: copy.badge.partial, color: 'orange' },
  imported: { label: copy.badge.imported, color: 'green' },
  // Grey, never red: no longer being updated is a decision, not a fault.
  stale: { label: copy.badge.stale, color: 'gray' },
};

export interface ImportMarkActions {
  /** Opens the confirmation; the mark itself is the dialog's to write. */
  onMark: (organo: Organo) => void;
  onUnmark: (organo: Organo) => void;
  /** The Órgano whose write is in flight, so only its own switch locks. */
  markingId?: string;
}

function Indicator({ state }: { state: MarkState }) {
  const badge = BADGE[state];
  if (badge) {
    return (
      <Badge variant="light" color={badge.color} styles={UNTRUNCATED_LABEL}>
        {badge.label}
      </Badge>
    );
  }
  // An inactive Órgano says why its switch is off rather than showing a state it
  // does not have; everything else with nothing stored shows the dash.
  return (
    <Text size="sm" c="dimmed">
      {state === 'ineligible' ? copy.badge.ineligible : copy.badge.none}
    </Text>
  );
}

/**
 * One row's contratos menores: the mark, and what is stored under it.
 *
 * The whole cell carries the tooltip rather than the badge alone — a disabled
 * `Switch` fires no pointer events, so a tooltip on the control itself would be
 * unreachable for the one row that most needs to explain itself. That row also
 * states its reason on screen, because a reason only on hover is no reason at
 * all for a reader who never hovers.
 */
export function ImportMarkCell({
  organo,
  actions,
}: {
  organo: Organo;
  actions: ImportMarkActions;
}) {
  const state = markState(organo);

  return (
    // Mantine opens a tooltip on hover alone by default, which would leave what
    // an Órgano is holding — the one place *parcial* versus *completo* is said —
    // reachable by pointer only. `focusin` bubbles, so focusing the switch
    // inside opens it.
    <Tooltip
      label={markTooltip(organo, state)}
      events={{ hover: true, focus: true, touch: true }}
      multiline
      w={260}
      withArrow
    >
      {/* Stacks below `sm`: at 360 px a switch and a badge side by side would
          squeeze the Órgano name past wrapping and into a page-wide scroll. */}
      <Flex direction={{ base: 'column', sm: 'row' }} align="center" gap="xs" wrap="nowrap">
        <Switch
          size="sm"
          checked={organo.importable}
          disabled={!organo.active || actions.markingId === organo.id}
          aria-label={`${copy.markLabel}: ${organo.name}`}
          onChange={() => (organo.importable ? actions.onUnmark(organo) : actions.onMark(organo))}
        />
        <Indicator state={state} />
      </Flex>
    </Tooltip>
  );
}
