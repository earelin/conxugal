import { Center, Loader, VisuallyHidden } from '@mantine/core';

import { strings } from '../lib/strings';

/**
 * Mantine's Loader is a bare decorative span, so on its own it empties a region
 * and refills it without a screen reader ever being told why. The live region
 * and the label are what make the wait perceivable rather than just visible.
 */
export function LoadingIndicator() {
  return (
    <Center py="xl" role="status">
      <Loader />
      <VisuallyHidden>{strings.loading}</VisuallyHidden>
    </Center>
  );
}
