import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { strings } from '../../../shared/lib/strings';
import { termoNameSchema, type TermoNameValues } from './termoName';
import { isDuplicateSiblingName, type Refusal, termoRefusal } from './termoRefusal';

/**
 * Create and rename are the same form: one validated name, and one pair of
 * places to put a refusal — a duplicate sibling name on the field the
 * administrator has to edit, everything else on a banner above it. Only what
 * they submit differs, so that is all the dialogs keep for themselves.
 */
export function useTermoNameForm(defaultName: string) {
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const form = useForm<TermoNameValues>({
    resolver: zodResolver(termoNameSchema),
    defaultValues: { name: defaultName },
  });

  return {
    form,
    refusal,
    clearRefusal: () => setRefusal(null),
    reportRefusal: (error: unknown) => {
      if (isDuplicateSiblingName(error)) {
        form.setError('name', { message: strings.admin.organos.termo.duplicateSiblingName });
      } else {
        setRefusal(termoRefusal(error));
      }
    },
  };
}
