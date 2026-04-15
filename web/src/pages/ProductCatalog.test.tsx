import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ProductCatalog from './ProductCatalog';
import { useCartStore } from '@/store/cartStore';

vi.mock('@/lib/api', () => ({
  fetchProducts: vi.fn(),
}));

import { fetchProducts } from '@/lib/api';
const mockFetchProducts = vi.mocked(fetchProducts);

function renderWithRouter() {
  return render(
    <BrowserRouter>
      <ProductCatalog />
    </BrowserRouter>
  );
}

const MOCK_PRODUCTS = [
  {
    id: 'p1',
    name: 'Mechanical Keyboard',
    description: 'Cherry MX Brown',
    price: 149.99,
    currency: 'USD',
    stockQuantity: 50,
  },
  {
    id: 'p2',
    name: 'Wireless Mouse',
    description: 'Ergonomic',
    price: 79.99,
    currency: 'USD',
    stockQuantity: 0,
  },
];

describe('ProductCatalog', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart();
    vi.clearAllMocks();
  });

  it('renders loading spinner initially', () => {
    mockFetchProducts.mockReturnValue(new Promise(() => {}));
    renderWithRouter();
    expect(document.querySelector('.animate-pulse')).toBeTruthy();
  });

  it('renders products after fetch', async () => {
    mockFetchProducts.mockResolvedValue(MOCK_PRODUCTS);
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument();
      expect(screen.getByText('Wireless Mouse')).toBeInTheDocument();
    });
  });

  it('shows error state when fetch fails', async () => {
    mockFetchProducts.mockRejectedValue(new Error('Network error'));
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText(/Failed to load products/)).toBeInTheDocument();
    });
  });

  it('disables Add to Cart for out-of-stock products', async () => {
    mockFetchProducts.mockResolvedValue(MOCK_PRODUCTS);
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument();
    });

    const buttons = screen.getAllByRole('button', { name: /Add to Cart/i });
    // Second product (Wireless Mouse) has stockQuantity=0
    expect(buttons[1]).toBeDisabled();
  });

  it('adds product to cart when clicking Add to Cart', async () => {
    mockFetchProducts.mockResolvedValue(MOCK_PRODUCTS);
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument();
    });

    const buttons = screen.getAllByRole('button', { name: /Add to Cart/i });
    await userEvent.click(buttons[0]);

    expect(useCartStore.getState().totalItems()).toBe(1);
    expect(useCartStore.getState().items[0].product.name).toBe('Mechanical Keyboard');
  });

  it('shows empty state when no products', async () => {
    mockFetchProducts.mockResolvedValue([]);
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('No products available')).toBeInTheDocument();
    });
  });
});
