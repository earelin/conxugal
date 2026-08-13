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
