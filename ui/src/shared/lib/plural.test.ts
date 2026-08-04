import { describe, expect, it } from 'vitest';

import { singularOrPlural, type Word } from './plural';

const ORGANO: Word = { singular: 'órgano', plural: 'órganos' };

describe('singularOrPlural', () => {
  it('takes the singular for exactly one and the plural for any other count', () => {
    expect(singularOrPlural(1, ORGANO)).toBe(ORGANO.singular);
    expect(singularOrPlural(2, ORGANO)).toBe(ORGANO.plural);
    expect(singularOrPlural(42, ORGANO)).toBe(ORGANO.plural);
  });

  it('takes the plural for none, which Galician counts like the rest rather than like one', () => {
    expect(singularOrPlural(0, ORGANO)).toBe(ORGANO.plural);
  });
});
