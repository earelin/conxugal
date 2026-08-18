import { Alert, Card, type MantineColor, Select, Stack } from '@mantine/core';
import { IconInfoCircle, IconPlayerPause } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useOutletContext, useSearchParams } from 'react-router';

import type { OrganoOutletContext } from '../../shared/entities/organo';
import { strings } from '../../shared/lib/strings';
import { chosenYear, sectionSummary } from './summary';

const copy = strings.contratosMenores;

const YEAR_PARAM = 'year';

/**
 * A standing fact about the section rather than a report of something that just
 * happened, so `role="status"` rather than the assertive `role="alert"` Mantine
 * defaults an `Alert` to — the same policy `shared/ui/StatusAlert.tsx` states.
 * That control is not reused here: it is dismissible, and neither of these two
 * stops being true because a reader closed it.
 */
function SectionStatement({
  color,
  icon,
  title,
  body,
}: {
  color: MantineColor;
  icon: ReactNode;
  title: string;
  body: string;
}) {
  return (
    <Alert variant="light" color={color} role="status" title={title} icon={icon}>
      {body}
    </Alert>
  );
}

/**
 * An Órgano's contratos menores: what the section says about itself, and the
 * year the rest of it is scoped to.
 *
 * It reads nothing. The years and both flags arrive as outlet context from the
 * Órgano page's single member read, and are narrowed here because this is the
 * feature that owns the shape — the page carries the summary without looking
 * inside it, and neither slice imports the other.
 *
 * The chosen year lives in the query string rather than in state, so the control
 * and the list it scopes cannot disagree about it and a selection is a link
 * somebody can send. Arriving without one derives the default and leaves the URL
 * alone: rewriting it on mount would put a redirect in every reader's history
 * for a choice they have not made yet.
 */
export function ContratosMenoresSection() {
  const { family } = useOutletContext<OrganoOutletContext>();
  const summary = sectionSummary(family);
  const [searchParams, setSearchParams] = useSearchParams();
  const year = chosenYear(searchParams.get(YEAR_PARAM), summary.years);

  // In the order they arrive, which is newest first: the ordering is the
  // server's answer, and re-sorting here would be this module holding a second
  // opinion about it.
  const options = summary.years.map((offered) => String(offered));

  return (
    <Stack gap="md">
      {summary.partial && (
        <SectionStatement
          color="indigo"
          icon={<IconInfoCircle size={18} />}
          title={copy.partialTitle}
          body={copy.partialBody}
        />
      )}
      {/* Independent of `partial`, and never folded into one status with it: an
          Órgano unmarked halfway through its initial import is both, and a
          single status would have to lie in exactly that case. */}
      {!summary.updating && (
        <SectionStatement
          color="gray"
          icon={<IconPlayerPause size={18} />}
          title={copy.notUpdatedTitle}
          body={copy.notUpdatedBody}
        />
      )}
      <Card withBorder radius="md" padding="md">
        <Select
          label={copy.yearLabel}
          labelProps={{ size: 'xs', fw: 700, c: 'dimmed', tt: 'uppercase' }}
          data={options}
          value={String(year)}
          // Nothing but years: no placeholder, no clear control and no deselect,
          // so the chooser has no state in which it offers something the domain
          // does not have.
          allowDeselect={false}
          clearable={false}
          w={180}
          onChange={(selected) => {
            if (selected === null) {
              return;
            }
            const next = new URLSearchParams(searchParams);
            next.set(YEAR_PARAM, selected);
            setSearchParams(next);
          }}
        />
      </Card>
    </Stack>
  );
}
