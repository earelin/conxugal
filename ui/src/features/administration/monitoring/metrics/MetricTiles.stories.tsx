import { SimpleGrid } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';

import { HeapTile, HttpTile, SystemLoadTile, ThreadsTile } from './MetricTiles';
import { HISTORY, LATEST, SHORT_HISTORY } from './storyFixtures';

/**
 * The four tiles across the top of the metrics panel. They share one shape —
 * label, figure, caption, sparkline — and one set of props, so they are stored
 * together and every story draws all four side by side.
 *
 * The three stream states are the axis worth browsing: `connecting` skeletons
 * the figure and the caption at the height the real content will take, `live`
 * is the ordinary state, and `reconnecting` dims the last known figure rather
 * than clearing it.
 */
function MetricTileRow(props: Parameters<typeof HeapTile>[0]) {
  return (
    <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="md">
      <HeapTile {...props} />
      <SystemLoadTile {...props} />
      <ThreadsTile {...props} />
      <HttpTile {...props} />
    </SimpleGrid>
  );
}

const meta = {
  component: MetricTileRow,
  subcomponents: { HeapTile, SystemLoadTile, ThreadsTile, HttpTile },
  tags: ['autodocs'],
  args: { state: 'live', latest: LATEST, history: HISTORY },
} satisfies Meta<typeof MetricTileRow>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * A full history buffer, so the first three captions name the peak of their
 * run. *Peticións HTTP* is the exception: its caption only ever reports the
 * change since the previous sample, whatever the history holds.
 */
export const Live: Story = {};

/** No sample has arrived yet: skeletons stand in at the final height. */
export const Connecting: Story = {
  args: { state: 'connecting', latest: null, history: [] },
};

/** The connection dropped; the last figures stay, dimmed, and stop moving. */
export const Reconnecting: Story = {
  args: { state: 'reconnecting' },
};

/** Early in a run, where the first three captions count the samples still missing. */
export const FillingHistory: Story = {
  args: { latest: SHORT_HISTORY[SHORT_HISTORY.length - 1], history: SHORT_HISTORY },
};

/**
 * The first sample, which is also the first history entry — the stream writes
 * both in one handler, so a tile never has a figure with nothing behind it.
 */
export const FirstSample: Story = {
  args: { latest: LATEST, history: [LATEST] },
};
