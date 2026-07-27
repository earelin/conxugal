import { strings } from '../../../shared/lib/strings';

export function orNoValue<T>(value: T | null | undefined, format: (value: T) => string): string {
  return value != null ? format(value) : strings.admin.dashboard.metrics.noValue;
}
