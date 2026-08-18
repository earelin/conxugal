import { Alert, Card, type MantineColor, Select, Stack } from '@mantine/core';
import { IconInfoCircle, IconPlayerPause } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { Navigate, useLocation, useOutletContext, useSearchParams } from 'react-router';

import type { OrganoOutletContext } from '../../shared/entities/organo';
import { strings } from '../../shared/lib/strings';
import { chosenYear, sectionSummary } from './summary';

const copy = strings.contratosMenores;

const YEAR_PARAM = 'year';

/**
 * The query string with this year named in it, and everything else it was
 * already carrying left where it was. Both ways the year can change — a reader
 * choosing one, and a stale one being corrected — write it through here, so
 * neither can drift from the other in what it preserves.
 */
function withYear(searchParams: URLSearchParams, year: string): string {
  const next = new URLSearchParams(searchParams);
  next.set(YEAR_PARAM, year);
  return next.toString();
}

interface SectionStatementProps {
  color: MantineColor;
  icon: ReactNode;
  title: string;
  body: string;
}

/**
 * A standing fact about the section rather than a report of something that just
 * happened, so `role="status"` rather than the assertive `role="alert"` Mantine
 * defaults an `Alert` to — the same policy `shared/ui/StatusAlert.tsx` states.
 * That control is not reused here: it is dismissible, and neither of these two
 * stops being true because a reader closed it.
 */
function SectionStatement({ color, icon, title, body }: SectionStatementProps) {
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
 * somebody can send.
 */
export function ContratosMenoresSection() {
  const { family } = useOutletContext<OrganoOutletContext>();
  const summary = sectionSummary(family);
  const { pathname, hash } = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const asked = searchParams.get(YEAR_PARAM);
  const year = chosenYear(asked, summary.years);

  // A year the Órgano has no contracts in — or one spelled some other way — is
  // corrected in place rather than merely displayed over. Replacing rather than
  // pushing is what keeps this off the reader's history: they made no choice
  // here. Arriving with no year at all is left alone, which is a different case
  // — there the URL says nothing rather than something untrue.
  if (asked !== null && asked !== String(year)) {
    const search = `?${withYear(searchParams, String(year))}`;
    return <Navigate to={{ pathname, search, hash }} replace />;
  }

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
          // `fz`, not `size`: an input label takes its wrapper's styles, so a
          // `size` here is accepted and then ignored.
          labelProps={{ fz: 'xs', fw: 700, c: 'dimmed', tt: 'uppercase' }}
          data={options}
          value={String(year)}
          // Years and nothing else: no placeholder and no deselect, so there is
          // no state in which the chooser offers something the domain does not
          // have. That is also what leaves the `null` below unreachable — it is
          // narrowing, not a branch.
          allowDeselect={false}
          maw={180}
          onChange={(selected) => {
            if (selected === null) {
              return;
            }
            setSearchParams(withYear(searchParams, selected));
          }}
        />
      </Card>
    </Stack>
  );
}
