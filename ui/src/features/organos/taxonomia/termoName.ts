import { z } from 'zod';

import { strings } from '../../../shared/lib/strings';

// The server's own limit on a term name; mirrored so an over-long name is
// caught in the dialog rather than as a bare 400.
export const MAX_TERMO_NAME_LENGTH = 255;

/**
 * The only validation the client mirrors: what a blank or over-long name is
 * needs no knowledge of the tree. Sibling uniqueness, cycles and the
 * child-terms rule stay the server's, which remains the authority for every rule.
 *
 * The name is trimmed here, so the trimmed value is what gets submitted — the
 * server stores it stripped either way.
 */
export const termoNameSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, strings.admin.organos.termo.nameRequired)
    .max(MAX_TERMO_NAME_LENGTH, strings.admin.organos.termo.nameTooLong(MAX_TERMO_NAME_LENGTH)),
});

export type TermoNameValues = z.infer<typeof termoNameSchema>;
