import { ActionIcon, Group, Stack, Table, Text } from '@mantine/core';
import { IconExternalLink, IconInfoCircle } from '@tabler/icons-react';
import { type CSSProperties, type ReactNode, useId } from 'react';

import { strings } from '../../shared/lib/strings';
import { formatAmount, formatPublicationDate } from './contractFormat';
import type { ContratoMenor } from './contracts';

const copy = strings.contratosMenores;

// The admin tables' column-header treatment, which this one adopts unchanged.
const COLUMN_HEADER = { tt: 'uppercase', fz: 'xs', c: 'dimmed' } as const;

// A column whose content has a shape the source respects — a date, a figure, an
// icon — and which therefore gains nothing from wrapping.
const NARROW_COLUMN: CSSProperties = { whiteSpace: 'nowrap', width: '1%' };

// The duration is capped at 64 characters and the source spends them: a stated
// term can be a sentence rather than «12 meses». Narrow, but never `nowrap` —
// one such row on an unbreakable line would widen the table by some 400 px and
// squeeze the object down to a word per line, which is the opposite of what the
// widths are for. The awardee's name has no cap at all and so takes no width
// here either.
const NARROW_WRAPPING_COLUMN: CSSProperties = { width: '1%' };

// Held at module scope rather than written inline, so the identifier beneath
// every date and every awardee is handed the same object rather than a fresh one
// per row per render.
const UNBROKEN: CSSProperties = { whiteSpace: 'nowrap' };

/**
 * A value with the identifier that names it beneath, which two of the six
 * columns are: the date over the contract's own identifier, the awardee over its
 * fiscal one.
 *
 * Both identifiers are copied rather than read, which is what the monospace is
 * for, and dimmed because neither is the value its column is named after.
 * Neither breaks across lines either: half an identifier on one line and half on
 * the next is worse than a wider column.
 */
function WithIdentifierBeneath({
  identifier,
  children,
}: {
  identifier: string;
  children: ReactNode;
}) {
  return (
    <Stack gap={0}>
      {children}
      <Text size="xs" c="dimmed" ff="monospace" style={UNBROKEN}>
        {identifier}
      </Text>
    </Stack>
  );
}

/**
 * The two values a row can lack. Nothing else has one of these: a contract
 * without its publication date, its amount or its awardee never reaches a
 * reader, so there is no absent form of those three to write.
 */
function OrNotPublished({ children }: { children: string | null }): ReactNode {
  if (children === null) {
    // The same size as the value it stands in for. A marker that set its own
    // would change the height of every row carrying one.
    return (
      <Text size="sm" fs="italic" c="dimmed">
        {copy.notPublished}
      </Text>
    );
  }
  return <Text size="sm">{children}</Text>;
}

function ContractRow({ contract }: { contract: ContratoMenor }): ReactNode {
  const sourceId = String(contract.sourceId);
  return (
    <Table.Tr>
      <Table.Td style={NARROW_COLUMN}>
        <WithIdentifierBeneath identifier={sourceId}>
          <Text size="sm" fw={500}>
            {formatPublicationDate(contract.publicationDate)}
          </Text>
        </WithIdentifierBeneath>
      </Table.Td>
      <Table.Td>
        <OrNotPublished>{contract.obxecto}</OrNotPublished>
      </Table.Td>
      {/* Text, deliberately: the operador route belongs to another spec's read
          feature, and a link to a route that 404s is worse than none. That
          feature adds the crossing here. */}
      <Table.Td>
        <WithIdentifierBeneath identifier={contract.awardee.fiscalId}>
          <Text size="sm">{contract.awardee.name}</Text>
        </WithIdentifierBeneath>
      </Table.Td>
      <Table.Td ta="right" fw={600} style={NARROW_COLUMN}>
        {formatAmount(contract.amount)}
      </Table.Td>
      <Table.Td style={NARROW_WRAPPING_COLUMN}>
        <OrNotPublished>{contract.duration}</OrNotPublished>
      </Table.Td>
      <Table.Td ta="right" style={NARROW_COLUMN}>
        {/* An `ActionIcon` rather than a bare anchor around the glyph: the icon
            alone is an 18 px target, which is below what a finger can be asked
            to hit — and it sits right beside the gesture that scrolls this
            table sideways. */}
        <ActionIcon
          component="a"
          variant="subtle"
          href={contract.sourceUrl}
          target="_blank"
          rel="noreferrer"
          // The column repeats down the page and «Fonte» names no contract, so
          // the only thing that tells one of these links from the next is the
          // contract it says it opens.
          aria-label={copy.sourceLinkLabel(sourceId)}
        >
          <IconExternalLink size={18} aria-hidden />
        </ActionIcon>
      </Table.Td>
    </Table.Tr>
  );
}

/**
 * Every attribute the system holds for a contrato menor, on one row: the family
 * has no detail view to click into, which is why the row is wide.
 *
 * The object, the awardee's name and the duration are rendered exactly as they
 * were returned — nothing truncates, case-folds or reformats them. Only the date
 * and the amount are written in a spelling of this list's own, which is
 * presentation rather than a correction of what the source published.
 */
export function ContratosMenoresTable({ contracts }: { contracts: ContratoMenor[] }) {
  const caveatId = useId();
  return (
    <Stack gap="xs">
      {/* Six columns need more than a 360 px card can give, so the table scrolls
          inside its own region rather than dragging the whole page sideways. The
          region takes focus and a name of its own: without them a keyboard
          reader tabs into the source links in the last column, arrives scrolled
          fully right, and has nothing focusable further left to bring it back. */}
      <Table.ScrollContainer
        minWidth={720}
        type="native"
        tabIndex={0}
        role="region"
        aria-label={copy.tableLabel}
      >
        <Table aria-label={copy.tableLabel} verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr>
              <Table.Th {...COLUMN_HEADER}>{copy.columnDate}</Table.Th>
              <Table.Th {...COLUMN_HEADER}>{copy.columnObxecto}</Table.Th>
              <Table.Th {...COLUMN_HEADER}>{copy.columnAwardee}</Table.Th>
              {/* Two lines, and the second is not decoration: an amount whose
                  VAT is unstated invites comparison against thresholds that
                  exclude it. */}
              <Table.Th {...COLUMN_HEADER} ta="right" style={NARROW_COLUMN}>
                <Stack gap={0}>
                  <span>{copy.columnAmount}</span>
                  <span>{copy.columnAmountVat}</span>
                </Stack>
              </Table.Th>
              {/* The caveat is described from the header rather than repeated on
                  every row: one statement covers all of them and reads once. The
                  `ⓘ` carries it to a reader who sees it, and `aria-describedby`
                  to one who does not — the sentence is named from the column it
                  is about rather than left to be met after the last row. */}
              <Table.Th
                {...COLUMN_HEADER}
                style={NARROW_WRAPPING_COLUMN}
                aria-describedby={caveatId}
              >
                <Group gap={4} wrap="nowrap">
                  {copy.columnDuration}
                  <IconInfoCircle size={14} aria-hidden />
                </Group>
              </Table.Th>
              <Table.Th {...COLUMN_HEADER} ta="right" style={NARROW_COLUMN}>
                {copy.columnSource}
              </Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {contracts.map((contract) => (
              <ContractRow key={contract.sourceId} contract={contract} />
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {/* Outside the scrolling region, deliberately. As a `<caption>` it sat
          inside the 720 px the table is held to, so at a 360 px viewport its
          lines ran off the side and each had to be read by scrolling right and
          back again. A table may scroll sideways; a sentence may not. */}
      <Group gap={6} wrap="nowrap" align="flex-start">
        <IconInfoCircle size={14} aria-hidden />
        <Text id={caveatId} size="xs" c="dimmed">
          {copy.durationCaveat}
        </Text>
      </Group>
    </Stack>
  );
}
