import { describe, expect, it } from 'vitest';

import { formatCount } from './number';

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
