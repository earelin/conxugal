import { describe, expect, it } from 'vitest';

import { foldForSearch, matches } from './organoSearch';

describe('foldForSearch', () => {
  it('strips accents and case so a typed name matches an accented one', () => {
    expect(foldForSearch('Educación')).toBe(foldForSearch('EDUCACION'));
    expect(foldForSearch('Saúde')).toBe('saude');
  });
});

describe('matches', () => {
  it('finds an accented name from an unaccented query', () => {
    expect(matches('Ávila', 'avila')).toBe(true);
    expect(matches('Servizo Galego de Saúde', 'saude')).toBe(true);
  });

  it('finds an unaccented name from an accented query', () => {
    expect(matches('Avila', 'Ávila')).toBe(true);
  });

  it('ignores letter case on either side', () => {
    expect(matches('Concello de Vigo', 'VIGO')).toBe(true);
    expect(matches('CONCELLO DE VIGO', 'vigo')).toBe(true);
  });

  it('finds a name by a fragment inside it, not only by its opening', () => {
    expect(matches('Instituto Galego da Vivenda e Solo', 'galego')).toBe(true);
    expect(matches('Instituto Galego da Vivenda e Solo', 'vivenda e')).toBe(true);
  });

  it('answers false for a query the name does not hold', () => {
    expect(matches('Servizo Galego de Saúde', 'sanidde')).toBe(false);
  });

  it('lets a blank query through, leaving it to the caller to refuse one', () => {
    expect(matches('Ávila', '')).toBe(true);
  });
});
