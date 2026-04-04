import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { fetchOrder } from '@/lib/api';
import { useOrderWebSocket } from '@/hooks/useWebSocket';
import { Order, OrderStatus } from '@/types';

const STEPS: OrderStatus[] = [
  'PENDING',
  'PAYMENT_REQUESTED',
  'PAYMENT_COMPLETED',
  'INVENTORY_REQUESTED',
  'CONFIRMED',
];

const STEP_LABELS: Record<string, string> = {
  PENDING: 'Order Placed',
  PAYMENT_REQUESTED: 'Payment Requested',
  PAYMENT_COMPLETED: 'Payment Completed',
  INVENTORY_REQUESTED: 'Checking Inventory',
  CONFIRMED: 'Order Confirmed',
  CANCELLED: 'Order Cancelled',
  REFUND_REQUESTED: 'Refund Requested',
};

function getStepIndex(status: OrderStatus): number {
  const idx = STEPS.indexOf(status);
  return idx === -1 ? -1 : idx;
}

function StatusBadge({ status }: { status: OrderStatus }) {
  const colors: Record<string, string> = {
    CONFIRMED: 'bg-green-500/10 text-green-400 ring-green-500/20',
    CANCELLED: 'bg-red-500/10 text-red-400 ring-red-500/20',
    REFUND_REQUESTED: 'bg-orange-500/10 text-orange-400 ring-orange-500/20',
  };
  const color = colors[status] ?? 'bg-nexus-500/10 text-nexus-400 ring-nexus-500/20';

  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-medium ring-1 ring-inset ${color}`}>
      {STEP_LABELS[status] ?? status}
    </span>
  );
}

export default function OrderTracking() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    fetchOrder(id)
      .then(setOrder)
      .catch(() => setError('Failed to load order'))
      .finally(() => setLoading(false));
  }, [id]);

  const handleWsUpdate = useCallback(
    (update: { orderId: string; status: string }) => {
      if (update.orderId === id) {
        setOrder((prev) =>
          prev ? { ...prev, status: update.status as OrderStatus, updatedAt: new Date().toISOString() } : prev
        );
      }
    },
    [id]
  );

  useOrderWebSocket(handleWsUpdate);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-nexus-500 border-t-transparent" />
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <span className="text-5xl mb-4">⚠️</span>
        <p className="text-lg font-medium">{error ?? 'Order not found'}</p>
        <Link to="/orders" className="mt-4 text-nexus-400 hover:text-nexus-300 transition-colors">
          ← Back to orders
        </Link>
      </div>
    );
  }

  const isCancelled = order.status === 'CANCELLED';
  const isRefund = order.status === 'REFUND_REQUESTED';
  const currentStepIndex = getStepIndex(order.status);
  const terminalSteps = isCancelled || isRefund;

  return (
    <div>
      <Link to="/orders" className="inline-flex items-center gap-1 text-sm text-gray-400 hover:text-white transition-colors mb-6">
        ← Back to orders
      </Link>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Order Tracking</h1>
          <p className="mt-1 text-sm text-gray-400 font-mono">
            {order.id}
          </p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      <div className="grid gap-8 lg:grid-cols-3">
        {/* Timeline */}
        <div className="lg:col-span-2 rounded-2xl border border-gray-800 bg-gray-900 p-6">
          <h2 className="text-lg font-semibold text-white mb-6">Status Timeline</h2>
          <div className="relative ml-4">
            {STEPS.map((step, i) => {
              const isActive = i <= currentStepIndex && !terminalSteps;
              const isCurrent = i === currentStepIndex && !terminalSteps;
              const isPast = i < currentStepIndex && !terminalSteps;

              return (
                <div key={step} className="relative flex gap-4 pb-8 last:pb-0">
                  {/* Connector line */}
                  {i < STEPS.length - 1 && (
                    <div className="absolute left-[11px] top-8 h-full w-0.5">
                      <div
                        className={`h-full w-full transition-colors duration-500 ${
                          isPast ? 'bg-nexus-500' : 'bg-gray-700'
                        }`}
                      />
                    </div>
                  )}
                  {/* Dot */}
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={`${step}-${isActive}`}
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      className={`relative z-10 mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 transition-colors duration-500 ${
                        isCurrent
                          ? 'border-nexus-400 bg-nexus-500 shadow-lg shadow-nexus-500/30'
                          : isPast
                            ? 'border-nexus-500 bg-nexus-600'
                            : 'border-gray-600 bg-gray-800'
                      }`}
                    >
                      {isPast && (
                        <svg className="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" strokeWidth={3} stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                        </svg>
                      )}
                      {isCurrent && (
                        <div className="h-2 w-2 rounded-full bg-white animate-pulse" />
                      )}
                    </motion.div>
                  </AnimatePresence>
                  {/* Label */}
                  <div>
                    <p
                      className={`font-medium transition-colors duration-500 ${
                        isActive ? 'text-white' : 'text-gray-500'
                      }`}
                    >
                      {STEP_LABELS[step]}
                    </p>
                    {isActive && (
                      <p className="text-xs text-gray-500 mt-0.5">
                        {isCurrent
                          ? new Date(order.updatedAt).toLocaleString()
                          : i === 0
                            ? new Date(order.createdAt).toLocaleString()
                            : ''}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}

            {/* Cancelled / Refund terminal state */}
            {terminalSteps && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="relative flex gap-4 pt-2"
              >
                <div
                  className={`relative z-10 mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                    isCancelled
                      ? 'border-red-400 bg-red-500 shadow-lg shadow-red-500/30'
                      : 'border-orange-400 bg-orange-500 shadow-lg shadow-orange-500/30'
                  }`}
                >
                  <svg className="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" strokeWidth={3} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </div>
                <div>
                  <p className={`font-medium ${isCancelled ? 'text-red-400' : 'text-orange-400'}`}>
                    {STEP_LABELS[order.status]}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {new Date(order.updatedAt).toLocaleString()}
                  </p>
                </div>
              </motion.div>
            )}
          </div>
        </div>

        {/* Order details sidebar */}
        <div className="rounded-2xl border border-gray-800 bg-gray-900 p-6 h-fit">
          <h2 className="text-lg font-semibold text-white mb-4">Order Details</h2>
          <ul className="space-y-3">
            {order.items.map((item) => (
              <li key={item.id} className="flex items-center justify-between text-sm">
                <div>
                  <p className="text-gray-200">{item.productName}</p>
                  <p className="text-gray-500">Qty: {item.quantity} × ${item.unitPrice.toFixed(2)}</p>
                </div>
                <p className="font-medium text-white">${item.subtotal.toFixed(2)}</p>
              </li>
            ))}
          </ul>
          <div className="mt-4 border-t border-gray-800 pt-4 flex items-center justify-between">
            <span className="text-gray-400">Total</span>
            <span className="text-xl font-bold text-white">
              ${order.totalAmount.toFixed(2)}
            </span>
          </div>
          <div className="mt-4 text-xs text-gray-500 space-y-1">
            <p>Created: {new Date(order.createdAt).toLocaleString()}</p>
            <p>Updated: {new Date(order.updatedAt).toLocaleString()}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
