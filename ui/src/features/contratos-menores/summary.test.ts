import { describe, expect, it } from 'vitest';

import { chosenYear, type PublicationYears } from './summary';

const YEARS: PublicationYears = [2025, 2024, 2023];

describe('chosenYear', () => {
  it('takes a year the Órgano has visible contracts in', () => {
    expect(chosenYear('2025', YEARS)).toBe(2025);
    expect(chosenYear('2024', YEARS)).toBe(2024);
    expect(chosenYear('2023', YEARS)).toBe(2023);
  });

  it('opens on the most recent year when the URL names none', () => {
    expect(chosenYear(null, YEARS)).toBe(2025);
    expect(chosenYear('', YEARS)).toBe(2025);
  });

  it('lands on the default rather than an error for a year the Órgano has none in', () => {
    // Both are years the domain knows; neither is one this Órgano offers, which
    // is what a link outliving an import looks like.
    expect(chosenYear('2019', YEARS)).toBe(2025);
    expect(chosenYear('2026', YEARS)).toBe(2025);
  });

  // Each of these spells 2024, a year the Órgano does have, so falling back to
  // 2025 is what says the spelling was refused rather than accepted.
  it('refuses anything that is not a run of digits', () => {
    expect(chosenYear('abc', YEARS)).toBe(2025);
    expect(chosenYear(' 2024', YEARS)).toBe(2025);
    expect(chosenYear('2024 ', YEARS)).toBe(2025);
    expect(chosenYear('+2024', YEARS)).toBe(2025);
    expect(chosenYear('2024.0', YEARS)).toBe(2025);
  });

  it('refuses a year written in another base or in exponent form', () => {
    // `Number` reads each of these as 2024, so without the digit guard a URL
    // could name a year in a spelling the API never takes.
    expect(chosenYear('0x7e8', YEARS)).toBe(2025);
    expect(chosenYear('0o3750', YEARS)).toBe(2025);
    expect(chosenYear('2.024e3', YEARS)).toBe(2025);
  });

  it('reads a padded year as the year it spells', () => {
    expect(chosenYear('02024', YEARS)).toBe(2024);
  });

  it('opens on the only year an Órgano with one has', () => {
    const only: PublicationYears = [2024];
    expect(chosenYear(null, only)).toBe(2024);
    expect(chosenYear('2025', only)).toBe(2024);
  });
});
