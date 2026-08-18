import { describe, expect, it } from 'vitest';

import { askedPage } from './askedPage';

const TOTAL_PAGES = 37;

describe('askedPage', () => {
  it('takes a page inside the selection, padding and all', () => {
    expect(askedPage('5', TOTAL_PAGES)).toBe(5);
    expect(askedPage('1', TOTAL_PAGES)).toBe(1);
    expect(askedPage('37', TOTAL_PAGES)).toBe(37);
    expect(askedPage('007', TOTAL_PAGES)).toBe(7);
    expect(askedPage(' 5 ', TOTAL_PAGES)).toBe(5);
  });

  it('refuses a page outside the selection rather than clamping it to one', () => {
    expect(askedPage('0', TOTAL_PAGES)).toBeNull();
    expect(askedPage('38', TOTAL_PAGES)).toBeNull();
    expect(askedPage('-1', TOTAL_PAGES)).toBeNull();
  });

  it('refuses anything that is not a run of digits', () => {
    expect(askedPage('abc', TOTAL_PAGES)).toBeNull();
    expect(askedPage('5abc', TOTAL_PAGES)).toBeNull();
    expect(askedPage('5.5', TOTAL_PAGES)).toBeNull();
    expect(askedPage('5.0', TOTAL_PAGES)).toBeNull();
    expect(askedPage('+5', TOTAL_PAGES)).toBeNull();
  });

  it('refuses a page written in another base or in exponent form', () => {
    // `Number` reads each of these as a page well inside the selection — 16,
    // 15, 5, 10 — so without the digit guard the jump would land on a page the
    // reader never asked for.
    expect(askedPage('0x10', TOTAL_PAGES)).toBeNull();
    expect(askedPage('0o17', TOTAL_PAGES)).toBeNull();
    expect(askedPage('0b101', TOTAL_PAGES)).toBeNull();
    expect(askedPage('1e1', TOTAL_PAGES)).toBeNull();
  });

  it('refuses an empty box and an untouched one alike', () => {
    expect(askedPage('', TOTAL_PAGES)).toBeNull();
    expect(askedPage('   ', TOTAL_PAGES)).toBeNull();
    expect(askedPage(null, TOTAL_PAGES)).toBeNull();
  });

  it('refuses every page when the selection has none', () => {
    expect(askedPage('1', 0)).toBeNull();
  });
});
