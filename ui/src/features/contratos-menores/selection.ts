import type { PublicationYears } from './summary';

export const YEAR_PARAM = 'year';
export const SORT_PARAM = 'sort';
export const PAGE_PARAM = 'page';

/**
 * The whole set of orderings, spelled exactly as the API takes them — two keys
 * by two directions, and nothing else. The API refuses anything outside this
 * set with a 400 rather than degrading it to the default, so a value that
 * cannot be one of these four must not be sent on.
 */
export const SORTS = [
  'publicationDate,desc',
  'publicationDate,asc',
  'amount,desc',
  'amount,asc',
] as const;

export type Sort = (typeof SORTS)[number];

/** The API's own default, spelled here because the response never states it. */
export const DEFAULT_SORT: Sort = 'publicationDate,desc';

export const FIRST_PAGE = 1;

/** One Órgano's contracts of one year, in one ordering, one page at a time. */
export interface Selection {
  year: number;
  sort: Sort;
  page: number;
}

/** A write of the selection: whichever of the three it names, and no more. */
export type SelectionChange = Partial<Selection>;

/**
 * Decimal digits only. `Number` would otherwise read `0x7e8` as 2024 and
 * `2.024e3` as 2024 too, so a URL could name a year — or a page — in a spelling
 * the API never takes. Deliberately not trimmed: whitespace in a query string
 * was put there by something, not typed by somebody.
 */
const DIGITS = /^\d+$/;

/**
 * The year the section opens on: the one the URL asks for where the Órgano has
 * contracts in it, and the most recent otherwise.
 *
 * An unknown year is not an error and not an empty list — it is a URL that has
 * outlived the selection it named, which happens as soon as an import moves on.
 * Landing on the default is the answer; `years` is newest first, so its first
 * entry is that default.
 */
export function chosenYear(asked: string | null, years: PublicationYears): number {
  if (!DIGITS.test(asked ?? '')) {
    return years[0];
  }
  const year = Number(asked);
  return years.includes(year) ? year : years[0];
}

/**
 * The ordering the URL asks for, or the default where it asks for none of the
 * four.
 *
 * Falling back rather than forwarding is what keeps a pasted URL working: the
 * API refuses an ordering outside the closed set, so sending one on would turn
 * a stale link into an error page. The URL is authoritative about the ordering
 * — the response states none — so this is also the parser that reads back what
 * the control wrote.
 */
export function chosenSort(asked: string | null): Sort {
  return SORTS.find((sort) => sort === asked) ?? DEFAULT_SORT;
}

/**
 * The largest page the API takes, its `page` being an `int32`. A larger one is a
 * 400 rather than an empty page, so it cannot be left to the clamp below to
 * correct — it would reach the reader as the error state instead of as the last
 * page.
 */
const LAST_PAGE = 2_147_483_647;

/**
 * The page the URL asks for, or the first.
 *
 * A page past the end of the *selection* is not refused here: the API answers it
 * with an empty page carrying the selection's true totals, and the list clamps
 * to the last page from that answer. Nothing here knows how many pages a
 * selection has. What is refused is a page outside the range the API itself
 * takes, which is a different thing and one this module does know.
 */
export function chosenPage(asked: string | null): number {
  if (!DIGITS.test(asked ?? '')) {
    return FIRST_PAGE;
  }
  const page = Number(asked);
  return page >= FIRST_PAGE && page <= LAST_PAGE ? page : FIRST_PAGE;
}

/** Everything the URL says about the selection, in the API's own spelling. */
export function readSelection(params: URLSearchParams, years: PublicationYears): Selection {
  return {
    year: chosenYear(params.get(YEAR_PARAM), years),
    sort: chosenSort(params.get(SORT_PARAM)),
    page: chosenPage(params.get(PAGE_PARAM)),
  };
}

/**
 * The current parameters with this change written into them, and everything
 * else they were carrying left where it was.
 *
 * **Naming a year or a sort drops the page.** A page number describes a
 * position in a selection, so it means nothing once the selection changes — and
 * holding that rule here rather than at each control is what keeps it from
 * having to be remembered by whichever control is added next.
 *
 * That rule wins over a `page` named in the same change, which is therefore
 * ignored rather than written: a caller asking for a new ordering *and* a page
 * within it is asking for two things the rule says cannot both hold, and
 * dropping to the first page is the one it says happens.
 */
export function withSelection(params: URLSearchParams, change: SelectionChange): URLSearchParams {
  const next = new URLSearchParams(params);
  if (change.year !== undefined) {
    next.set(YEAR_PARAM, String(change.year));
  }
  if (change.sort !== undefined) {
    next.set(SORT_PARAM, change.sort);
  }
  if (change.year !== undefined || change.sort !== undefined) {
    next.delete(PAGE_PARAM);
  } else if (change.page !== undefined) {
    next.set(PAGE_PARAM, String(change.page));
  }
  return next;
}

function misspelt(params: URLSearchParams, key: string, shown: string): boolean {
  const asked = params.get(key);
  return asked !== null && asked !== shown;
}

/**
 * The parameters respelled where the URL states the selection some other way
 * than the section is showing it, or `null` where the two already agree.
 *
 * A stale year, an ordering that is not one of the four, a page written as `0`
 * — each is corrected in place rather than merely displayed over, so a reader is
 * never left holding a link that says one thing while the section shows
 * another. A parameter the URL does not carry is left absent: there the URL says
 * nothing rather than something untrue, and writing a default into it would put
 * a choice the reader never made into their history.
 */
export function respelling(params: URLSearchParams, selection: Selection): URLSearchParams | null {
  const change: SelectionChange = {};
  if (misspelt(params, YEAR_PARAM, String(selection.year))) {
    change.year = selection.year;
  }
  if (misspelt(params, SORT_PARAM, selection.sort)) {
    change.sort = selection.sort;
  }
  // Only where neither of the two above is being corrected: those drop the page
  // rather than respell it, the selection it counted from having changed
  // underneath it.
  if (
    change.year === undefined &&
    change.sort === undefined &&
    misspelt(params, PAGE_PARAM, String(selection.page))
  ) {
    change.page = selection.page;
  }
  return Object.keys(change).length === 0 ? null : withSelection(params, change);
}
