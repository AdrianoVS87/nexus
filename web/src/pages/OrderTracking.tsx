import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { fetchOrder } from '@/lib/api';
import { useOrderWebSocket } from '@/hooks/useWebSocket';
import { useToastStore } from '@/store/toastStore';
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
    PENDING: 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20',
    PAYMENT_REQUESTED: 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20',
    PAYMENT_COMPLETED: 'bg-blue-500/10 text-blue-400 ring-blue-500/20',
    INVENTORY_REQUESTED: 'bg-blue-500/10 text-blue-400 ring-blue-500/20',
    CONFIRMED: 'bg-green-500/10 text-green-400 ring-green-500/20',
    CANCELLED: 'bg-red-500/10 text-red-400 ring-red-500/20',
    REFUND_REQUESTED: 'bg-red-500/10 text-red-400 ring-red-500/20',
  };
  const color = colors[status] ?? 'bg-yellow-500/10 text-yellow-400 ring-yellow-500/20';

  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-medium ring-1 ring-inset ${color}`}>
      {STEP_LABELS[status] ?? status}
    </span>
  );
}

function SkeletonTimeline() {
  return (
    <div className="lg:col-span-2 rounded-2xl border border-surface-border bg-surface-card p-6 animate-pulse">
      <div className="h-6 w-36 rounded bg-surface-border/50 mb-6" />
      <div className="ml-4 space-y-6">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex gap-4">
            <div className="h-6 w-6 rounded-full bg-surface-border/50 shrink-0" />
            <div className="space-y-1 flex-1">
              <div className="h-4 w-36 rounded bg-surface-border/50" />
              <div className="h-3 w-24 rounded bg-surface-border/30" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function SkeletonDetails() {
  return (
    <div className="rounded-2xl border border-surface-border bg-surface-card p-6 h-fit animate-pulse">
      <div className="h-6 w-32 rounded bg-surface-border/50 mb-4" />
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex justify-between">
            <div className="space-y-1">
              <div className="h-4 w-28 rounded bg-surface-border/50" />
              <div className="h-3 w-20 rounded bg-surface-border/30" />
            </div>
            <div className="h-4 w-14 rounded bg-surface-border/50" />
          </div>
        ))}
      </div>
    </div>
  );
}

export default function OrderTracking() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const addToast = useToastStore((s) => s.addToast);

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
        const newStatus = update.status as OrderStatus;
        setOrder((prev) =>
          prev ? { ...prev, status: newStatus, updatedAt: new Date().toISOString() } : prev
        );
        const label = STEP_LABELS[newStatus] ?? newStatus;
        if (newStatus === 'CONFIRMED') {
          addToast(`Order ${label.toLowerCase()}!`, 'success');
        } else if (newStatus === 'CANCELLED' || newStatus === 'REFUND_REQUESTED') {
          addToast(`Order status: ${label}`, 'error');
        } else {
          addToast(`Order status: ${label}`, 'info');
        }
      }
    },
    [id, addToast]
  );

  useOrderWebSocket(handleWsUpdate, id);

  if (loading) {
    return (
      <div>
        <div className="h-4 w-28 rounded bg-surface-border/30 animate-pulse mb-6" />
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between mb-8">
          <div className="space-y-2 animate-pulse">
            <div className="h-7 w-44 rounded bg-surface-border/50" />
            <div className="h-4 w-64 rounded bg-surface-border/30" />
          </div>
        </div>
        <div className="grid gap-8 lg:grid-cols-3">
          <SkeletonTimeline />
          <SkeletonDetails />
        </div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-red-500/10 mb-4">
          <svg className="h-10 w-10 text-red-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
          </svg>
        </div>
        <p className="text-lg font-medium">{error ?? 'Order not found'}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 min-h-[44px] rounded-xl bg-nexus-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 transition-all duration-150"
        >
          Retry
        </button>
        <Link to="/orders" className="mt-2 text-nexus-400 hover:text-nexus-300 transition-colors min-h-[44px] flex items-center">
          &larr; Back to orders
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
      <Link to="/orders" className="inline-flex items-center gap-1 text-sm text-gray-400 hover:text-white transition-colors mb-6 min-h-[44px]">
        &larr; Back to orders
      </Link>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Order Tracking</h1>
          <p className="mt-1 text-sm text-gray-400 font-mono break-all">
            {order.id}
          </p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      <div className="grid gap-8 lg:grid-cols-3">
        {/* Timeline */}
        <div className="lg:col-span-2 rounded-2xl border border-surface-border bg-surface-card p-6">
          <h2 className="text-lg font-semibold text-white mb-6">Status Timeline</h2>

          {/* Horizontal timeline — desktop */}
          <div className="hidden lg:block">
            <div className="flex items-start justify-between">
              {STEPS.map((step, i) => {
                const isActive = i <= currentStepIndex && !terminalSteps;
                const isCurrent = i === currentStepIndex && !terminalSteps;
                const isPast = i < currentStepIndex && !terminalSteps;

                return (
                  <div key={step} className="flex flex-1 flex-col items-center text-center relative">
                    {/* Connector line */}
                    {i < STEPS.length - 1 && (
                      <div className="absolute top-3 left-1/2 w-full h-0.5">
                        <div
                          className={`h-full w-full transition-colors duration-500 ${
                            isPast ? 'bg-nexus-500' : 'bg-surface-border'
                          }`}
                        />
                      </div>
                    )}
                    {/* Dot */}
                    <motion.div
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      className={`relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 transition-colors duration-500 ${
                        isCurrent
                          ? 'border-nexus-400 bg-nexus-500 shadow-lg shadow-nexus-500/30'
                          : isPast
                            ? 'border-nexus-500 bg-nexus-600'
                            : 'border-surface-border-light bg-surface-bg'
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
                    {/* Label */}
                    <p
                      className={`mt-2 text-xs font-medium transition-colors duration-500 ${
                        isActive ? 'text-white' : 'text-gray-500'
                      }`}
                    >
                      {STEP_LABELS[step]}
                    </p>
                  </div>
                );
              })}
            </div>
            {terminalSteps && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-center justify-center gap-2 mt-6 pt-4 border-t border-surface-border"
              >
                <div
                  className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                    isCancelled
                      ? 'border-red-400 bg-red-500 shadow-lg shadow-red-500/30'
                      : 'border-orange-400 bg-orange-500 shadow-lg shadow-orange-500/30'
                  }`}
                >
                  <svg className="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" strokeWidth={3} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </div>
                <p className={`font-medium ${isCancelled ? 'text-red-400' : 'text-orange-400'}`}>
                  {STEP_LABELS[order.status]}
                </p>
                <p className="text-xs text-gray-500 ml-2">
                  {new Date(order.updatedAt).toLocaleString()}
                </p>
              </motion.div>
            )}
          </div>

          {/* Vertical timeline — mobile/tablet */}
          <div className="relative ml-4 lg:hidden">
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
                          isPast ? 'bg-nexus-500' : 'bg-surface-border'
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
                            : 'border-surface-border-light bg-surface-bg'
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
        <div className="rounded-2xl border border-surface-border bg-surface-card p-6 h-fit">
          <h2 className="text-lg font-semibold text-white mb-4">Order Details</h2>
          <ul className="space-y-3">
            {order.items.map((item) => (
              <li key={item.id} className="flex items-center justify-between text-sm">
                <div>
                  <p className="text-gray-200">{item.productName}</p>
                  <p className="text-gray-500">Qty: {item.quantity} &times; ${item.unitPrice.toFixed(2)}</p>
                </div>
                <p className="font-medium text-white">${item.subtotal.toFixed(2)}</p>
              </li>
            ))}
          </ul>
          <div className="mt-4 border-t border-surface-border pt-4 flex items-center justify-between">
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
