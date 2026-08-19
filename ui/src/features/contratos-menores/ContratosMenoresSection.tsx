import { Alert, Card, Group, type MantineColor, Select, Stack } from '@mantine/core';
import { IconInfoCircle, IconPlayerPause } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import {
  Navigate,
  useLocation,
  useNavigate,
  useOutletContext,
  useSearchParams,
} from 'react-router';

import type { OrganoOutletContext } from '../../shared/entities/organo';
import { strings } from '../../shared/lib/strings';
import { ContratosMenoresList } from './ContratosMenoresList';
import {
  readSelection,
  respelling,
  type SelectionChange,
  type Sort,
  withSelection,
} from './selection';
import { sectionSummary } from './summary';

const copy = strings.contratosMenores;

/**
 * The four orderings a reader can ask for, each paired with the spelling the API
 * takes it in. Written out rather than derived from `SORTS`, so a control and
 * its copy cannot part company: adding an entry to either list without the other
 * fails to type-check.
 */
const SORT_OPTIONS: { value: Sort; label: string }[] = [
  { value: 'publicationDate,desc', label: copy.sort.dateDesc },
  { value: 'publicationDate,asc', label: copy.sort.dateAsc },
  { value: 'amount,desc', label: copy.sort.amountDesc },
  { value: 'amount,asc', label: copy.sort.amountAsc },
];

// An input label takes its wrapper's styles, so a `size` here is accepted and
// then ignored — `fz` is what reaches it.
const CHOOSER_LABEL = { fz: 'xs', fw: 700, c: 'dimmed', tt: 'uppercase' } as const;

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
 * An Órgano's contratos menores: what the section says about itself, the
 * selection the rest of it is scoped to, and the page of contracts that
 * selection names.
 *
 * It reads no Órgano. The years and both flags arrive as outlet context from the
 * page's single member read, and are narrowed here because this is the feature
 * that owns the shape — the page carries the summary without looking inside it,
 * and neither slice imports the other. The slice's own read is the list below,
 * and it is the only one it makes.
 *
 * The selection lives in the query string rather than in state, so no control
 * can disagree with the list it scopes, a selection is a link somebody can send,
 * and the browser's back button walks it. The family is the *path*, so switching
 * tab discards the whole selection along with the route — it describes one that
 * no longer exists.
 */
export function ContratosMenoresSection() {
  const { organo, family } = useOutletContext<OrganoOutletContext>();
  const summary = sectionSummary(family);
  const { pathname, hash } = useLocation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const selection = readSelection(searchParams, summary.years);

  // The whole location this section sits at, hash included: every write of the
  // selection goes through it, so a fragment survives a choice as well as a
  // correction.
  function locationWith(params: URLSearchParams) {
    return { pathname, search: `?${params.toString()}`, hash };
  }

  // A selection the URL states some other way than it is being shown — a year
  // the Órgano has no contracts in, an ordering outside the four, a page written
  // as `0` — is corrected in place rather than merely displayed over. Replacing
  // rather than pushing is what keeps this off the reader's history: they made
  // no choice here. A parameter the URL does not carry is left absent, which is
  // a different case — there the URL says nothing rather than something untrue.
  const corrected = respelling(searchParams, selection);
  if (corrected !== null) {
    return <Navigate to={locationWith(corrected)} replace />;
  }

  // In the order they arrive, which is newest first: the ordering is the
  // server's answer, and re-sorting here would be this module holding a second
  // opinion about it.
  const years = summary.years.map((offered) => String(offered));

  // A choice, so it is pushed: going back returns to the selection the reader
  // came from.
  function choose(change: SelectionChange) {
    void navigate(locationWith(withSelection(searchParams, change)));
  }

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
        <Stack gap="md">
          {/* The two controls that scope the list, above what they scope. They
              wrap rather than shrink: the ordering's entries are whole Galician
              sentences, and a narrow viewport that squeezed them would leave a
              reader choosing between four truncations. */}
          <Group gap="md" align="flex-end" wrap="wrap">
            <Select
              label={copy.yearLabel}
              labelProps={CHOOSER_LABEL}
              data={years}
              // The chooser's entries are strings, the selection's year a
              // number. Which spelling the URL takes is `respelling`'s, not
              // this — here it is only what the field displays.
              value={String(selection.year)}
              // Years and nothing else: no placeholder and no deselect, so there
              // is no state in which the chooser offers something the domain
              // does not have. That is also what leaves the `null` below
              // unreachable — it is narrowing, not a branch.
              allowDeselect={false}
              maw={180}
              onChange={(selected) => {
                if (selected === null) {
                  return;
                }
                choose({ year: Number(selected) });
              }}
            />
            <Select
              label={copy.sortLabel}
              labelProps={CHOOSER_LABEL}
              data={SORT_OPTIONS}
              value={selection.sort}
              // Four entries and no fifth state: the API refuses an ordering
              // outside them, and a control that could be emptied would ask for
              // one.
              allowDeselect={false}
              // Enough for the longest entry at a comfortable width, and free to
              // take the whole line when the two controls wrap.
              flex="1 1 320px"
              maw={360}
              // At a 360 px viewport even the whole line is shorter than the
              // longest entry, and an input clips rather than wrapping. An
              // ellipsis is what says the name goes on; a bare cut reads as a
              // different entry from the one chosen.
              styles={{ input: { textOverflow: 'ellipsis' } }}
              onChange={(selected) => {
                if (selected === null) {
                  return;
                }
                choose({ sort: selected });
              }}
            />
          </Group>
          {/* Below the choosers and inside the card, so what it is scoped to sits
              above it. The two statements stay outside, which is what keeps them
              on screen while this is loading or has failed. It writes the page
              itself, through the same helper this writes the other two with —
              the page is the one part of the selection that answers to what came
              back, not only to what was clicked. */}
          <ContratosMenoresList organoId={organo.id} selection={selection} />
        </Stack>
      </Card>
    </Stack>
  );
}
