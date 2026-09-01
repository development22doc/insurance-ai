/// <reference types="vitest" />
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../contexts/AuthContext';
import { HomePage } from '../HomePage';
import { ProductsPage } from '../ProductsPage';
import { Header } from '../../components/layout/Header';

describe('Public pages and navigation', () => {
  it('renders header links for unauthenticated user', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <Header />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Products/i)).toBeInTheDocument();
    expect(screen.getByText(/Claims/i)).toBeInTheDocument();
    expect(screen.getByText(/About/i)).toBeInTheDocument();
    expect(screen.getByText(/Contact/i)).toBeInTheDocument();
    expect(screen.getByText(/Login/i)).toBeInTheDocument();
    expect(screen.getByText(/Register/i)).toBeInTheDocument();
  });

  it('renders home and products routes', () => {
    const homeRouter = createMemoryRouter([
      { path: '/', element: <HomePage /> },
    ], { initialEntries: ['/'] });

    render(<RouterProvider router={homeRouter} />);

    expect(screen.getByRole('heading', { level: 1, name: /Faster claims. Fairer outcomes./i })).toBeInTheDocument();

    const productsRouter = createMemoryRouter([
      { path: '/products', element: <ProductsPage /> },
    ], { initialEntries: ['/products'] });

    render(<RouterProvider router={productsRouter} />);
    expect(screen.getByRole('heading', { level: 1, name: /Products/i })).toBeInTheDocument();
  });
});
