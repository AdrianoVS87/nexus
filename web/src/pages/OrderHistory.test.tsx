import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import OrderHistory from './OrderHistory';

vi.mock('@/lib/api', () => ({
  fetchUserOrders: vi.fn(),
}));

import { fetchUserOrders } from '@/lib/api';
const mockFetchUserOrders = vi.mocked(fetchUserOrders);

function renderWithRouter() {
  return render(
    <BrowserRouter>
      <OrderHistory />
    </BrowserRouter>
  );
}

describe('OrderHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading spinner initially', () => {
    mockFetchUserOrders.mockReturnValue(new Promise(() => {}));
    renderWithRouter();
    expect(document.querySelector('.animate-pulse')).toBeTruthy();
  });

  it('renders order list after fetch', async () => {
    mockFetchUserOrders.mockResolvedValue({
      content: [
        {
          id: 'order-1',
          userId: 'user-1',
          status: 'CONFIRMED',
          totalAmount: 149.99,
          currency: 'USD',
          items: [],
          createdAt: '2026-04-06T10:00:00Z',
          updatedAt: '2026-04-06T10:00:05Z',
        },
        {
          id: 'order-2',
          userId: 'user-1',
          status: 'CANCELLED',
          totalAmount: 79.99,
          currency: 'USD',
          items: [],
          createdAt: '2026-04-06T09:00:00Z',
          updatedAt: '2026-04-06T09:00:03Z',
        },
      ],
    });

    renderWithRouter();

    await waitFor(() => {
      // Amounts render in both desktop table and mobile cards
      expect(screen.getAllByText('$149.99').length).toBeGreaterThan(0);
      expect(screen.getAllByText('$79.99').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Confirmed').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Cancelled').length).toBeGreaterThan(0);
    });
  });

  it('shows empty state when no orders', async () => {
    mockFetchUserOrders.mockResolvedValue({ content: [] });
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('No orders yet')).toBeInTheDocument();
      expect(screen.getByText('Browse Products')).toBeInTheDocument();
    });
  });

  it('shows error state when fetch fails', async () => {
    mockFetchUserOrders.mockRejectedValue(new Error('Server error'));
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText('Failed to load orders')).toBeInTheDocument();
    });
  });
});
