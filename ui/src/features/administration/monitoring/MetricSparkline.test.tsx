import { MantineProvider } from '@mantine/core';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { theme } from '../../../app/theme';
import { MetricSparkline } from './MetricSparkline';
import type { RuntimeMetrics } from './metricsStream';

function renderSparkline(history: RuntimeMetrics[]) {
  return render(
    <MantineProvider theme={theme}>
      <MetricSparkline
        history={history}
        variant="live"
        select={(sample) => sample.jvm?.threadCount ?? null}
      />
    </MantineProvider>,
  );
}

describe('MetricSparkline', () => {
  it('is inert and hidden from assistive tech in its empty (no-history) state', () => {
    const { container } = renderSparkline([]);

    const wrapper = container.querySelector('[aria-hidden]');
    expect(wrapper).not.toBeNull();
    expect(wrapper).toHaveAttribute('inert');
  });

  it('is inert and hidden from assistive tech once it has data, so it is never a tab stop', () => {
    const sample: RuntimeMetrics = {
      timestamp: '2026-07-18T09:30:00Z',
      jvm: { threadCount: 10 },
    };
    const { container } = renderSparkline([sample]);

    const wrapper = container.querySelector('[aria-hidden]');
    expect(wrapper).not.toBeNull();
    expect(wrapper).toHaveAttribute('inert');
  });
});
