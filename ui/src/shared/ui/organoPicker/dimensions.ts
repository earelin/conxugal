// Chevron and leaf spacer share this width, which lines labels up across a
// level whether or not the row has children.
export const MARKER_SIZE = 14;

// The filter box's own adornment, which lines up with nothing in the tree.
export const SEARCH_ICON_SIZE = 16;

// Relative to the window rather than a pixel count, so the dropdown still fits
// under the trigger on a short viewport. Bounds the tree and the matches
// alike: either can outgrow the window on the real catalogue.
export const MAX_BODY_HEIGHT = '60vh';
