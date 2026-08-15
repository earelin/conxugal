// Chevron and leaf spacer share this width, which lines labels up across a
// level whether or not the row has children.
export const MARKER_SIZE = 14;

// Relative to the window rather than a pixel count, so the dropdown still fits
// under the trigger on a short viewport. Bounds the tree and the matches
// alike: either can outgrow the window on the real catalogue.
export const MAX_BODY_HEIGHT = '60vh';
