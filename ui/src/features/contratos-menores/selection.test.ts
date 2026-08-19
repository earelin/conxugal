import { describe, expect, it } from 'vitest';

import {
  chosenPage,
  chosenSort,
  chosenYear,
  DEFAULT_SORT,
  readSelection,
  respelling,
  type Selection,
  SORTS,
  withSelection,
} from './selection';
import type { PublicationYears } from './summary';

const YEARS: PublicationYears = [2025, 2024, 2023];

function params(search: string) {
  return new URLSearchParams(search);
}

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
    expect(chosenYear('2019', YEARS)).toBe(2025);
    expect(chosenYear('2026', YEARS)).toBe(2025);
  });

  it('refuses anything that is not a run of digits', () => {
    expect(chosenYear('abc', YEARS)).toBe(2025);
    expect(chosenYear(' 2024', YEARS)).toBe(2025);
    expect(chosenYear('2024 ', YEARS)).toBe(2025);
    expect(chosenYear('+2024', YEARS)).toBe(2025);
    expect(chosenYear('2024.0', YEARS)).toBe(2025);
  });

  it('refuses a year written in another base or in exponent form', () => {
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

describe('chosenSort', () => {
  it('takes each of the four orderings the API offers', () => {
    for (const sort of SORTS) {
      expect(chosenSort(sort)).toBe(sort);
    }
  });

  it('opens on the API’s own default when the URL names no ordering', () => {
    expect(chosenSort(null)).toBe('publicationDate,desc');
    expect(chosenSort('')).toBe('publicationDate,desc');
  });

  it('falls back rather than forwarding an ordering the API would refuse', () => {
    // Each of these is a 400 on the wire, so passing it on would turn a stale
    // link into an error page instead of a list.
    expect(chosenSort('obxecto,desc')).toBe(DEFAULT_SORT);
    expect(chosenSort('amount,descending')).toBe(DEFAULT_SORT);
    expect(chosenSort('amount')).toBe(DEFAULT_SORT);
    expect(chosenSort('amount,DESC')).toBe(DEFAULT_SORT);
    expect(chosenSort('publicationDate, desc')).toBe(DEFAULT_SORT);
    expect(chosenSort('amount,desc,extra')).toBe(DEFAULT_SORT);
  });
});

describe('chosenPage', () => {
  it('takes the page the URL names', () => {
    expect(chosenPage('1')).toBe(1);
    expect(chosenPage('37')).toBe(37);
  });

  it('opens on the first page when the URL names none', () => {
    expect(chosenPage(null)).toBe(1);
    expect(chosenPage('')).toBe(1);
  });

  it('refuses a page below the first one the API takes', () => {
    expect(chosenPage('0')).toBe(1);
    expect(chosenPage('-2')).toBe(1);
  });

  it('refuses a page in another base, in exponent form or with a fraction', () => {
    expect(chosenPage('0x10')).toBe(1);
    expect(chosenPage('1e1')).toBe(1);
    expect(chosenPage('2.5')).toBe(1);
    expect(chosenPage('dous')).toBe(1);
  });

  it('refuses a page beyond the largest the API takes', () => {
    // `page` is an int32 on the wire, so a larger one is a 400 — an error state
    // rather than the last page, which is what the clamp exists to avoid.
    expect(chosenPage('2147483647')).toBe(2147483647);
    expect(chosenPage('2147483648')).toBe(1);
    expect(chosenPage('9999999999')).toBe(1);
  });

  it('leaves a page past the end alone, having no way to know where the end is', () => {
    // The API answers it with an empty page carrying the true totals, and the
    // list clamps from that answer. Refusing it here would show page 1 while
    // the URL said otherwise.
    expect(chosenPage('9999')).toBe(9999);
  });
});

describe('readSelection', () => {
  it('reads all three from the query string', () => {
    expect(readSelection(params('?year=2024&sort=amount%2Cdesc&page=3'), YEARS)).toEqual({
      year: 2024,
      sort: 'amount,desc',
      page: 3,
    });
  });

  it('opens on the newest year, the default ordering and the first page', () => {
    expect(readSelection(params(''), YEARS)).toEqual({
      year: 2025,
      sort: DEFAULT_SORT,
      page: 1,
    });
  });
});

describe('withSelection', () => {
  it('writes each part in the spelling the API takes it in', () => {
    expect(withSelection(params(''), { year: 2024 }).toString()).toBe('year=2024');
    expect(withSelection(params(''), { sort: 'amount,desc' }).get('sort')).toBe('amount,desc');
    expect(withSelection(params(''), { page: 4 }).toString()).toBe('page=4');
  });

  it('replaces what is already there rather than adding a second one', () => {
    const next = withSelection(params('?year=2023'), { year: 2025 });
    expect(next.getAll('year')).toEqual(['2025']);
  });

  it('drops the page when the year changes', () => {
    const next = withSelection(params('?year=2025&sort=amount%2Cdesc&page=7'), { year: 2024 });
    expect(next.get('year')).toBe('2024');
    expect(next.get('sort')).toBe('amount,desc');
    // The page counted from a selection that no longer exists.
    expect(next.has('page')).toBe(false);
  });

  it('drops the page when the ordering changes', () => {
    const next = withSelection(params('?year=2025&page=7'), { sort: 'amount,asc' });
    expect(next.get('sort')).toBe('amount,asc');
    expect(next.has('page')).toBe(false);
  });

  it('keeps the year and the ordering when only the page changes', () => {
    const next = withSelection(params('?year=2024&sort=amount%2Casc&page=2'), { page: 5 });
    expect(next.get('year')).toBe('2024');
    expect(next.get('sort')).toBe('amount,asc');
    expect(next.get('page')).toBe('5');
  });

  it('drops the page even where the same change names one', () => {
    // The rule wins over the argument: a caller asking for a new ordering *and*
    // a page within it is asking for two things that cannot both hold.
    const next = withSelection(params('?page=7'), { sort: 'amount,desc', page: 4 });
    expect(next.has('page')).toBe(false);
  });

  it('leaves everything else in the query string where it was', () => {
    const next = withSelection(params('?tab=algo&year=2025'), { sort: 'amount,desc' });
    expect(next.get('tab')).toBe('algo');
  });
});

describe('respelling', () => {
  const shown: Selection = { year: 2025, sort: 'publicationDate,desc', page: 1 };

  it('says nothing when the URL already states what is shown', () => {
    expect(respelling(params('?year=2025&sort=publicationDate%2Cdesc&page=1'), shown)).toBeNull();
  });

  it('leaves a parameter the URL does not carry absent', () => {
    // A reader who has chosen nothing gets no entry in their history for a
    // choice they did not make.
    expect(respelling(params(''), shown)).toBeNull();
    expect(respelling(params('?year=2025'), shown)).toBeNull();
  });

  it('corrects a year the Órgano has no contracts in', () => {
    expect(respelling(params('?year=2019'), shown)?.toString()).toBe('year=2025');
  });

  it('respells a year written in a spelling the API does not take', () => {
    expect(respelling(params('?year=02025'), shown)?.toString()).toBe('year=2025');
  });

  it('corrects an ordering outside the four', () => {
    expect(respelling(params('?sort=obxecto'), shown)?.get('sort')).toBe('publicationDate,desc');
  });

  it('corrects a page the API would refuse', () => {
    expect(respelling(params('?page=0'), shown)?.toString()).toBe('page=1');
  });

  it('drops the page rather than respelling it when the year is corrected too', () => {
    const next = respelling(params('?year=2019&page=0'), shown);
    expect(next?.get('year')).toBe('2025');
    expect(next?.has('page')).toBe(false);
  });

  it('drops the page when the ordering named is not one of the four', () => {
    const next = respelling(params('?sort=obxecto&page=3'), shown);
    expect(next?.get('sort')).toBe('publicationDate,desc');
    expect(next?.has('page')).toBe(false);
  });

  it('keeps the page when the year is only spelled differently', () => {
    // 02025 and 2025 are the same year, so nothing moved and the page the link
    // named still counts from the selection it was written for.
    const next = respelling(params('?year=02025&page=5'), { ...shown, page: 5 });
    expect(next?.get('year')).toBe('2025');
    expect(next?.get('page')).toBe('5');
  });

  it('keeps the rest of the query string while correcting', () => {
    const next = respelling(params('?sort=amount%2Cdesc&year=2019'), {
      ...shown,
      sort: 'amount,desc',
    });
    expect(next?.get('year')).toBe('2025');
    expect(next?.get('sort')).toBe('amount,desc');
  });
});
