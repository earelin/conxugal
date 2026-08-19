/**
 * The abbreviated Galician months, pinned rather than read off the runtime.
 *
 * Two reasons, and either alone would be enough. `Intl` resolves `gl-ES` to
 * `12 de mar. de 2025`, which is not the spelling the screens taking
 * `formatCalendarDate` are drawn in; and the data behind it differs between
 * browser builds, so a suite asserting on a formatted date would pass on one
 * engine and fail on another.
 */
const MONTHS = ['xan', 'feb', 'mar', 'abr', 'mai', 'xuñ', 'xul', 'ago', 'set', 'out', 'nov', 'dec'];

/**
 * A calendar date — a day with no time and no zone, as `YYYY-MM-DD` — written
 * `12 mar 2025`.
 *
 * It sits beside `formatDate` rather than replacing it, and the two differ in
 * both halves. This one pins its own month names, where `formatDate` takes the
 * runtime's; and it splits the string rather than parsing it, because
 * `new Date('2025-03-12')` is midnight UTC and would show a reader west of
 * Greenwich the day before the one that was published. Reconciling them is its
 * own task: `formatDate`'s callers would change what they render.
 *
 * The date is taken at its published word — nothing validates the shape, so a
 * malformed one renders as the nonsense it is rather than being guessed at.
 */
export function formatCalendarDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('gl-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export function formatTime(date: Date): string {
  return date.toLocaleTimeString('gl-ES', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
}

/** The same clock without its seconds, for a time that is read rather than watched. */
export function formatHourMinute(date: Date): string {
  return date.toLocaleTimeString('gl-ES', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });
}

/**
 * A moment that may not be today. A bare clock reading is only unambiguous
 * within the day it belongs to, and an import that runs for days is read long
 * after the day it started on.
 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('gl-ES', { dateStyle: 'short', timeStyle: 'short' });
}
