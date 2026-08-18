import { describe, expect, it } from 'vitest';

import { formatAmount, formatPublicationDate } from './contractFormat';

describe('formatPublicationDate', () => {
  it('writes the day, the abbreviated Galician month and the year', () => {
    expect(formatPublicationDate('2025-03-12')).toBe('12 mar 2025');
  });

  it('drops the leading zero from a single-digit day', () => {
    expect(formatPublicationDate('2025-02-03')).toBe('3 feb 2025');
  });

  it('names all twelve months', () => {
    const everyMonth = Array.from({ length: 12 }, (_, index) =>
      formatPublicationDate(`2025-${String(index + 1).padStart(2, '0')}-01`),
    );

    expect(everyMonth).toEqual([
      '1 xan 2025',
      '1 feb 2025',
      '1 mar 2025',
      '1 abr 2025',
      '1 mai 2025',
      '1 xuñ 2025',
      '1 xul 2025',
      '1 ago 2025',
      '1 set 2025',
      '1 out 2025',
      '1 nov 2025',
      '1 dec 2025',
    ]);
  });

  it('states the calendar date that was published, at both ends of a year', () => {
    // The trap these guard: `new Date('2025-01-01')` is midnight UTC, so a
    // reader west of Greenwich would be shown 31 December 2024 for the first of
    // these and a reader east of it 1 January 2026 for the second. The dates
    // never reach a `Date`, so neither can happen.
    expect(formatPublicationDate('2025-01-01')).toBe('1 xan 2025');
    expect(formatPublicationDate('2025-12-31')).toBe('31 dec 2025');
  });
});

describe('formatAmount', () => {
  it('groups thousands with a dot and marks the decimals with a comma', () => {
    expect(formatAmount(12480)).toBe('12.480,00 €');
  });

  it('keeps the published cents rather than rounding them away', () => {
    expect(formatAmount(8750.5)).toBe('8.750,50 €');
  });

  it('writes an amount below a thousand with no grouping at all', () => {
    expect(formatAmount(940.25)).toBe('940,25 €');
  });

  it('groups every three digits of a large amount', () => {
    expect(formatAmount(1234567.89)).toBe('1.234.567,89 €');
  });

  it('pins both marks rather than reading them off the runtime', () => {
    // The separators are the assertion, not the digits: the suite runs across
    // browser builds whose gl-ES data disagrees, and one that resolved '.' as
    // the decimal mark would otherwise render 1,90 as "1 90".
    expect(formatAmount(1234.5)).toBe('1.234,50 €');
  });
});
