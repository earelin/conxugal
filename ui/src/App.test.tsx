import { screen } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { strings } from './shared/lib/strings';
import { mockCurrentUser, renderApp } from './test/renderApp';

describe('application shell', () => {
  beforeEach(() => {
    nock.disableNetConnect();
    mockCurrentUser('USER');
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders the shell with the product name and home content on /', () => {
    renderApp('/');

    expect(screen.getByRole('heading', { level: 1, name: strings.appName })).toBeInTheDocument();
    expect(screen.getByText(strings.home.title)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
  });

  it('shows the Galician not-found message inside the shell for unknown routes', () => {
    renderApp('/rota-que-non-existe');

    expect(screen.getByRole('heading', { level: 1, name: strings.appName })).toBeInTheDocument();
    expect(screen.getByText(strings.notFound.title)).toBeInTheDocument();
    expect(screen.getByText(strings.notFound.description)).toBeInTheDocument();
  });
});
