import { describe, expect, it } from 'vitest';

import { formatCount, formatEuros } from './number';

describe('formatCount', () => {
  it('groups digits into thousands separated by a space', () => {
    expect(formatCount(15_230)).toBe('15 230');
  });

  it('adds a separator for every group of three digits in large numbers', () => {
    expect(formatCount(1_234_567)).toBe('1 234 567');
  });

  it('does not add a separator for numbers under a thousand', () => {
    expect(formatCount(42)).toBe('42');
  });

  it('rounds a fractional input to the nearest integer before grouping', () => {
    expect(formatCount(1999.6)).toBe('2 000');
  });

  it('preserves a negative sign while still grouping the digits', () => {
    expect(formatCount(-15_230)).toBe('-15 230');
  });

  it('formats zero as a bare 0', () => {
    expect(formatCount(0)).toBe('0');
  });
});

describe('formatEuros', () => {
  // The separators are what these assert, not the digits: the suite runs across
  // browser builds whose gl-ES data disagrees, and one resolving '.' as the
  // decimal mark would otherwise render 1,90 as "1 90".
  it.each([
    { case: 'a whole amount, with cents it did not state', amount: 12_480, written: '12.480,00 €' },
    { case: 'the cents that were published', amount: 8750.5, written: '8.750,50 €' },
    { case: 'an amount below a thousand, ungrouped', amount: 940.25, written: '940,25 €' },
    {
      case: 'every three digits of a large amount',
      amount: 1_234_567.89,
      written: '1.234.567,89 €',
    },
    { case: 'a single grouping mark', amount: 1234.5, written: '1.234,50 €' },
  ])('writes $case as $written', ({ amount, written }) => {
    expect(formatEuros(amount)).toBe(written);
  });

  it('groups money with a dot where a count takes a space', () => {
    // The two marks differ on purpose, so this pins the difference rather than
    // leaving a later reader to read one as a mistake in the other.
    expect(formatEuros(12_480)).toContain('12.480');
    expect(formatCount(12_480)).toBe('12 480');
  });
});
