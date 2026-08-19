import { describe, expect, it } from 'vitest';

import { formatCalendarDate, formatDateTime, formatHourMinute, formatTime } from './date';

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

describe('formatCalendarDate', () => {
  it.each([
    { case: 'a two-digit day', iso: '2025-03-12', written: '12 mar 2025' },
    {
      case: 'a single-digit day, whose leading zero goes',
      iso: '2025-02-03',
      written: '3 feb 2025',
    },
    // The trap these two guard: `new Date('2025-01-01')` is midnight UTC, so a
    // reader west of Greenwich would be shown 31 December 2024 for the first and
    // a reader east of it 1 January 2026 for the second. Neither date reaches a
    // `Date`, so neither can happen.
    { case: 'the first day of a year', iso: '2025-01-01', written: '1 xan 2025' },
    { case: 'the last day of a year', iso: '2025-12-31', written: '31 dec 2025' },
  ])('writes $case as $written', ({ iso, written }) => {
    expect(formatCalendarDate(iso)).toBe(written);
  });

  it.each([
    { iso: '2025-01-15', written: '15 xan 2025' },
    { iso: '2025-02-15', written: '15 feb 2025' },
    { iso: '2025-03-15', written: '15 mar 2025' },
    { iso: '2025-04-15', written: '15 abr 2025' },
    { iso: '2025-05-15', written: '15 mai 2025' },
    { iso: '2025-06-15', written: '15 xuñ 2025' },
    { iso: '2025-07-15', written: '15 xul 2025' },
    { iso: '2025-08-15', written: '15 ago 2025' },
    { iso: '2025-09-15', written: '15 set 2025' },
    { iso: '2025-10-15', written: '15 out 2025' },
    { iso: '2025-11-15', written: '15 nov 2025' },
    { iso: '2025-12-15', written: '15 dec 2025' },
  ])('names the month in $iso, writing it $written', ({ iso, written }) => {
    expect(formatCalendarDate(iso)).toBe(written);
  });
});
