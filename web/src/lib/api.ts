import axios from 'axios';
import { Order, Product } from '../types';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

export async function fetchProducts(): Promise<Product[]> {
  const { data } = await api.get<Product[]>('/products');
  return data;
}

export async function fetchOrder(orderId: string): Promise<Order> {
  const { data } = await api.get<Order>(`/orders/${orderId}`);
  return data;
}

export async function fetchUserOrders(userId: string, page = 0): Promise<{ content: Order[] }> {
  const { data } = await api.get<{ content: Order[] }>(`/orders?userId=${userId}&page=${page}`);
  return data;
}

export interface CreateOrderPayload {
  userId: string;
  items: { productId: string; productName: string; quantity: number; unitPrice: number }[];
}

export async function createOrder(payload: CreateOrderPayload): Promise<Order> {
  const { data } = await api.post<Order>('/orders', payload);
  return data;
}

export default api;
