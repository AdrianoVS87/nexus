import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { fetchUserOrders } from '@/lib/api';
import { Order, OrderStatus } from '@/types';

const USER_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

const STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING: 'bg-gray-500/10 text-gray-400 ring-gray-500/20',
  PAYMENT_REQUESTED: 'bg-nexus-500/10 text-nexus-400 ring-nexus-500/20',
  PAYMENT_COMPLETED: 'bg-blue-500/10 text-blue-400 ring-blue-500/20',
  INVENTORY_REQUESTED: 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20',
  CONFIRMED: 'bg-green-500/10 text-green-400 ring-green-500/20',
  CANCELLED: 'bg-red-500/10 text-red-400 ring-red-500/20',
  REFUND_REQUESTED: 'bg-orange-500/10 text-orange-400 ring-orange-500/20',
};

const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  PAYMENT_REQUESTED: 'Payment Requested',
  PAYMENT_COMPLETED: 'Payment Completed',
  INVENTORY_REQUESTED: 'Checking Inventory',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  REFUND_REQUESTED: 'Refund Requested',
};

function truncateId(id: string) {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

const rowVariants = {
  hidden: { opacity: 0, x: -10 },
  show: { opacity: 1, x: 0 },
};

export default function OrderHistory() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchUserOrders(USER_ID)
      .then((res) => setOrders(res.content))
      .catch(() => setError('Failed to load orders'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-nexus-500 border-t-transparent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <span className="text-5xl mb-4">⚠️</span>
        <p className="text-lg font-medium">{error}</p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white">Your Orders</h1>
        <p className="mt-1 text-gray-400">Track and manage your purchases</p>
      </div>

      {orders.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-gray-400">
          <span className="text-6xl mb-4">📋</span>
          <p className="text-xl font-medium">No orders yet</p>
          <p className="text-sm mt-2">Your order history will appear here</p>
          <Link
            to="/"
            className="mt-6 rounded-xl bg-nexus-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 transition-colors"
          >
            Browse Products
          </Link>
        </div>
      ) : (
        <div className="rounded-2xl border border-gray-800 bg-gray-900 overflow-hidden">
          {/* Desktop table */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-800 text-left text-sm text-gray-400">
                  <th className="px-6 py-4 font-medium">Order ID</th>
                  <th className="px-6 py-4 font-medium">Status</th>
                  <th className="px-6 py-4 font-medium">Total</th>
                  <th className="px-6 py-4 font-medium">Date</th>
                  <th className="px-6 py-4 font-medium" />
                </tr>
              </thead>
              <motion.tbody
                initial="hidden"
                animate="show"
                transition={{ staggerChildren: 0.05 }}
              >
                {orders.map((order) => (
                  <motion.tr
                    key={order.id}
                    variants={rowVariants}
                    className="border-b border-gray-800/50 last:border-0 hover:bg-gray-800/30 transition-colors"
                  >
                    <td className="px-6 py-4 font-mono text-sm text-gray-300">
                      {truncateId(order.id)}
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${STATUS_COLORS[order.status]}`}
                      >
                        {STATUS_LABELS[order.status]}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-semibold text-white">
                      ${order.totalAmount.toFixed(2)}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-400">
                      {new Date(order.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <Link
                        to={`/orders/${order.id}`}
                        className="text-sm font-medium text-nexus-400 hover:text-nexus-300 transition-colors"
                      >
                        Track →
                      </Link>
                    </td>
                  </motion.tr>
                ))}
              </motion.tbody>
            </table>
          </div>

          {/* Mobile card list */}
          <div className="sm:hidden divide-y divide-gray-800">
            {orders.map((order) => (
              <Link
                key={order.id}
                to={`/orders/${order.id}`}
                className="flex items-center justify-between px-4 py-4 hover:bg-gray-800/30 transition-colors"
              >
                <div>
                  <p className="font-mono text-sm text-gray-300">{truncateId(order.id)}</p>
                  <span
                    className={`mt-1 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STATUS_COLORS[order.status]}`}
                  >
                    {STATUS_LABELS[order.status]}
                  </span>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-white">${order.totalAmount.toFixed(2)}</p>
                  <p className="text-xs text-gray-500">
                    {new Date(order.createdAt).toLocaleDateString()}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
