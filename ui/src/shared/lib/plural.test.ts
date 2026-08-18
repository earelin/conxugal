import { describe, expect, it } from 'vitest';

import { counted, singularOrPlural, type Word } from './plural';

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

describe('counted', () => {
  it('puts the count before the form that count calls for', () => {
    expect(counted(1, ORGANO)).toBe(`1 ${ORGANO.singular}`);
    expect(counted(0, ORGANO)).toBe(`0 ${ORGANO.plural}`);
    expect(counted(42, ORGANO)).toBe(`42 ${ORGANO.plural}`);
  });

  it('groups thousands the way formatCount does, with a space rather than a dot', () => {
    expect(counted(1832, ORGANO)).toBe(`1 832 ${ORGANO.plural}`);
  });
});
