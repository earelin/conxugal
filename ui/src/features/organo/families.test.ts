import { describe, expect, it } from 'vitest';

import { FAMILIES, familiesHeld } from './families';

const CONTRATOS_MENORES = FAMILIES[0];
const summary = { route: 'contratos-menores', summary: { years: [2025] } };

describe('familiesHeld', () => {
  it('holds the registry entry the read carries', () => {
    expect(familiesHeld({ contratosMenores: summary })).toEqual([CONTRATOS_MENORES]);
  });

  it('holds nothing for an Órgano with no family at all', () => {
    expect(familiesHeld({})).toEqual([]);
  });

  it('ignores a family this build does not know', () => {
    expect(familiesHeld({ licitacions: summary })).toEqual([]);
    expect(familiesHeld({ contratosMenores: summary, licitacions: summary })).toEqual([
      CONTRATOS_MENORES,
    ]);
  });

  it('answers on the key being present, whatever the entry beneath it is', () => {
    expect(familiesHeld({ contratosMenores: null })).toEqual([CONTRATOS_MENORES]);
  });
});

describe('FAMILIES', () => {
  it('names each family once, by key and by route segment alike', () => {
    expect(new Set(FAMILIES.map((family) => family.key)).size).toBe(FAMILIES.length);
    expect(new Set(FAMILIES.map((family) => family.path)).size).toBe(FAMILIES.length);
  });
});
