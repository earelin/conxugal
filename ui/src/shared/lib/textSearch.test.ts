import { describe, expect, it } from 'vitest';

import { foldForSearch, matches } from './textSearch';

describe('foldForSearch', () => {
  it.each([
    { text: 'Educación', folded: 'educacion' },
    { text: 'EDUCACION', folded: 'educacion' },
    { text: 'Saúde', folded: 'saude' },
    { text: 'Ávila', folded: 'avila' },
  ])('folds $text to $folded', ({ text, folded }) => {
    expect(foldForSearch(text)).toBe(folded);
  });
});

describe('matches', () => {
  it.each([
    { case: 'unaccented query, accented name', name: 'Ávila', query: 'avila' },
    { case: 'accented query, unaccented name', name: 'Avila', query: 'Ávila' },
    { case: 'upper-case query, lower-case name', name: 'Concello de Vigo', query: 'VIGO' },
    { case: 'lower-case query, upper-case name', name: 'CONCELLO DE VIGO', query: 'vigo' },
    { case: 'a fragment inside the name', name: 'Instituto Galego da Vivenda', query: 'galego' },
    {
      case: 'a fragment spanning two words',
      name: 'Instituto Galego da Vivenda',
      query: 'galego da',
    },
    // Refusing one is the caller's rule about what a surface shows, not this
    // function's about what a name holds.
    { case: 'a blank query, which holds nothing', name: 'Ávila', query: '' },
  ])('finds a name on $case', ({ name, query }) => {
    expect(matches(name, query)).toBe(true);
  });

  it.each([
    { case: 'a query the name does not hold', name: 'Servizo Galego de Saúde', query: 'sanidde' },
    {
      case: 'a fragment whose words are apart',
      name: 'Instituto Galego da Vivenda',
      query: 'galego vivenda',
    },
  ])('finds nothing on $case', ({ name, query }) => {
    expect(matches(name, query)).toBe(false);
  });
});
