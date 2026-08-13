/**
 * Mantine ellipsises a Badge's label once its column is squeezed, which turns
 * ACTIVO into "A…". A state badge has to stay readable at every width.
 */
export const UNTRUNCATED_LABEL = {
  root: { maxWidth: 'none' },
  label: { overflow: 'visible' },
};
