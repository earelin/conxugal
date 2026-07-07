import { createTheme } from '@mantine/core';

/**
 * Project-wide Mantine theme. Keep visual tokens (colors, radius, fonts)
 * centralised here so every screen stays consistent.
 */
export const theme = createTheme({
  primaryColor: 'indigo',
  defaultRadius: 'md',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  headings: {
    fontWeight: '600',
  },
});
