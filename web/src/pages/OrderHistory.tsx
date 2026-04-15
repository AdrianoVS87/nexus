import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { fetchUserOrders } from '@/lib/api';
import { Order, OrderStatus } from '@/types';

const USER_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

const STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING: 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20',
  PAYMENT_REQUESTED: 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20',
  PAYMENT_COMPLETED: 'bg-blue-500/10 text-blue-400 ring-blue-500/20',
  INVENTORY_REQUESTED: 'bg-blue-500/10 text-blue-400 ring-blue-500/20',
  CONFIRMED: 'bg-green-500/10 text-green-400 ring-green-500/20',
  CANCELLED: 'bg-red-500/10 text-red-400 ring-red-500/20',
  REFUND_REQUESTED: 'bg-red-500/10 text-red-400 ring-red-500/20',
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
  return id.length > 8 ? `${id.slice(0, 8)}\u2026` : id;
}

function SkeletonRow() {
  return (
    <div className="flex items-center justify-between px-6 py-4 animate-pulse">
      <div className="space-y-2">
        <div className="h-4 w-24 rounded bg-surface-border/50" />
        <div className="h-5 w-28 rounded-full bg-surface-border/30" />
      </div>
      <div className="text-right space-y-2">
        <div className="h-5 w-16 rounded bg-surface-border/50" />
        <div className="h-3 w-20 rounded bg-surface-border/30" />
      </div>
    </div>
  );
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
      <div>
        <div className="mb-8">
          <div className="h-8 w-40 rounded bg-surface-border/50 animate-pulse" />
          <div className="mt-2 h-4 w-56 rounded bg-surface-border/30 animate-pulse" />
        </div>
        <div className="rounded-2xl border border-surface-border bg-surface-card overflow-hidden divide-y divide-surface-border">
          {Array.from({ length: 5 }).map((_, i) => (
            <SkeletonRow key={i} />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-red-500/10 mb-4">
          <svg className="h-10 w-10 text-red-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
          </svg>
        </div>
        <p className="text-lg font-medium">{error}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 min-h-[44px] rounded-xl bg-nexus-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 transition-all duration-150"
        >
          Retry
        </button>
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
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-surface-card mb-4">
            <svg className="h-10 w-10 text-gray-500" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 0 0 2.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 0 0-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75 2.25 2.25 0 0 0-.1-.664m-5.8 0A2.251 2.251 0 0 1 13.5 2.25H15c1.012 0 1.867.668 2.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25ZM6.75 12h.008v.008H6.75V12Zm0 3h.008v.008H6.75V15Zm0 3h.008v.008H6.75V18Z" />
            </svg>
          </div>
          <p className="text-xl font-medium">No orders yet</p>
          <p className="text-sm mt-2">Your order history will appear here</p>
          <Link
            to="/"
            className="mt-6 min-h-[44px] flex items-center rounded-xl bg-nexus-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 transition-colors"
          >
            Browse Products
          </Link>
        </div>
      ) : (
        <div className="rounded-2xl border border-surface-border bg-surface-card overflow-hidden">
          {/* Desktop table */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-surface-border text-left text-sm text-gray-400">
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
                    className="border-b border-surface-border/50 last:border-0 hover:bg-white/[0.02] transition-all duration-150"
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
                        Track &rarr;
                      </Link>
                    </td>
                  </motion.tr>
                ))}
              </motion.tbody>
            </table>
          </div>

          {/* Mobile card list */}
          <div className="sm:hidden divide-y divide-surface-border">
            {orders.map((order) => (
              <Link
                key={order.id}
                to={`/orders/${order.id}`}
                className="flex items-center justify-between px-4 py-4 min-h-[72px] hover:bg-white/[0.02] transition-all duration-150"
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
