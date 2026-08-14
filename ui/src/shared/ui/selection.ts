/**
 * The palette of a selected row, wherever one is drawn: the open Órgano in the
 * browse tree, the chosen term in the assign dialog, the open term in the
 * administration tree.
 *
 * Mantine's `light` variant pair rather than a step off the indigo scale —
 * both sides of it are redefined per colour scheme, so a selected row keeps
 * its contrast in the dark theme instead of reading as dark blue on near-white.
 * A row that takes its background from Mantine's own selected state uses the
 * colour alone, which holds against either scheme of that background too.
 */
export const SELECTED_ROW_BG = 'var(--mantine-color-indigo-light)';
export const SELECTED_ROW_COLOR = 'var(--mantine-color-indigo-light-color)';
