import { describe, it, expect, beforeEach } from 'vitest';
import { useCartStore } from './cartStore';
import { Product } from '@/types';

const KEYBOARD: Product = {
  id: 'p1',
  name: 'Mechanical Keyboard',
  description: 'Cherry MX Brown',
  price: 149.99,
  currency: 'USD',
  stockQuantity: 50,
};

const MOUSE: Product = {
  id: 'p2',
  name: 'Wireless Mouse',
  description: 'Ergonomic',
  price: 79.99,
  currency: 'USD',
  stockQuantity: 120,
};

describe('cartStore', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart();
  });

  it('starts with empty cart', () => {
    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().totalItems()).toBe(0);
    expect(useCartStore.getState().totalAmount()).toBe(0);
  });

  it('adds a product', () => {
    useCartStore.getState().addItem(KEYBOARD);
    const { items } = useCartStore.getState();
    expect(items).toHaveLength(1);
    expect(items[0].product.id).toBe('p1');
    expect(items[0].quantity).toBe(1);
  });

  it('increments quantity when adding same product twice', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().addItem(KEYBOARD);
    const { items } = useCartStore.getState();
    expect(items).toHaveLength(1);
    expect(items[0].quantity).toBe(2);
  });

  it('adds different products as separate items', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().addItem(MOUSE);
    expect(useCartStore.getState().items).toHaveLength(2);
  });

  it('removes a product', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().addItem(MOUSE);
    useCartStore.getState().removeItem('p1');
    const { items } = useCartStore.getState();
    expect(items).toHaveLength(1);
    expect(items[0].product.id).toBe('p2');
  });

  it('updates quantity', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().updateQuantity('p1', 5);
    expect(useCartStore.getState().items[0].quantity).toBe(5);
  });

  it('calculates total amount correctly', () => {
    useCartStore.getState().addItem(KEYBOARD); // 149.99
    useCartStore.getState().addItem(MOUSE);    // 79.99
    useCartStore.getState().updateQuantity('p1', 2); // 2 * 149.99 = 299.98
    // 299.98 + 79.99 = 379.97
    expect(useCartStore.getState().totalAmount()).toBeCloseTo(379.97, 2);
  });

  it('calculates total items correctly', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().addItem(MOUSE);
    useCartStore.getState().updateQuantity('p1', 3);
    expect(useCartStore.getState().totalItems()).toBe(4); // 3 + 1
  });

  it('clears entire cart', () => {
    useCartStore.getState().addItem(KEYBOARD);
    useCartStore.getState().addItem(MOUSE);
    useCartStore.getState().clearCart();
    expect(useCartStore.getState().items).toEqual([]);
    expect(useCartStore.getState().totalItems()).toBe(0);
  });
});
