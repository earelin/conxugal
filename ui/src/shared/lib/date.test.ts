import { describe, expect, it } from 'vitest';

import { formatDateTime, formatHourMinute, formatTime } from './date';

describe('formatTime', () => {
  it('formats a time as zero-padded HH:mm:ss in 24-hour form', () => {
    const date = new Date(2026, 6, 18, 9, 5, 7);

    expect(formatTime(date)).toBe('09:05:07');
  });

  it('renders midnight as hour 00 rather than wrapping to 12', () => {
    const date = new Date(2026, 6, 18, 0, 0, 0);

    expect(formatTime(date)).toBe('00:00:00');
  });

  it('renders the 23rd hour without converting to 12-hour form', () => {
    const date = new Date(2026, 6, 18, 23, 59, 59);

    expect(formatTime(date)).toBe('23:59:59');
  });
});

describe('formatHourMinute', () => {
  it('drops the seconds a read time has no use for', () => {
    expect(formatHourMinute(new Date(2026, 6, 18, 9, 5, 7))).toBe('09:05');
  });

  it('keeps the 24-hour clock, so 23:00 never reads as 11', () => {
    expect(formatHourMinute(new Date(2026, 6, 18, 23, 0, 0))).toBe('23:00');
  });
});

describe('formatDateTime', () => {
  it('carries the day as well as the clock, for a moment that may not be today', () => {
    // Asserted as *containing* a time rather than against a full locale string:
    // which separators gl-ES resolves to is the runtime's business, and pinning
    // them would make this a test of the browser's locale data.
    const formatted = formatDateTime(new Date(2026, 6, 18, 9, 5).toISOString());

    expect(formatted).toContain('26');
    expect(formatted).toContain('9:05');
    expect(formatted).not.toBe(formatHourMinute(new Date(2026, 6, 18, 9, 5)));
  });
});
