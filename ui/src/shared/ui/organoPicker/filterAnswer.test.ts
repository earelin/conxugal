import { describe, expect, it } from 'vitest';

import { filterAnswer } from './filterAnswer';

describe('filterAnswer', () => {
  it('asks for the tree while nothing has been typed, whatever the list holds', () => {
    expect(filterAnswer('', 0)).toBe('tree');
    expect(filterAnswer('', 4)).toBe('tree');
  });

  it('asks for the matches once a query holds some', () => {
    expect(filterAnswer('saude', 1)).toBe('matches');
  });

  it('asks for the refusal, not the tree, when a query holds none', () => {
    // The two must not answer alike: one is a reply, the other is the state
    // before a question was asked.
    expect(filterAnswer('sanidde', 0)).toBe('none');
  });
});
