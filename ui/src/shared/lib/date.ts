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
