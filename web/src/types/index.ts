export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  stockQuantity: number;
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: string;
  userId: string;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_REQUESTED'
  | 'PAYMENT_COMPLETED'
  | 'INVENTORY_REQUESTED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'REFUND_REQUESTED';

export interface CartItem {
  product: Product;
  quantity: number;
}
