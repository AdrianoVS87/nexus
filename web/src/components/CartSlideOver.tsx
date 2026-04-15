import { Fragment, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useCartStore } from '@/store/cartStore';
import { useToastStore } from '@/store/toastStore';
import { createOrder } from '@/lib/api';

const USER_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

interface CartSlideOverProps {
  open: boolean;
  onClose: () => void;
}

export default function CartSlideOver({ open, onClose }: CartSlideOverProps) {
  const { items, removeItem, updateQuantity, clearCart, totalAmount } = useCartStore();
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCheckout = async () => {
    if (items.length === 0 || isSubmitting) return;
    setIsSubmitting(true);

    try {
      const order = await createOrder({
        userId: USER_ID,
        items: items.map((item) => ({
          productId: item.product.id,
          productName: item.product.name,
          quantity: item.quantity,
          unitPrice: item.product.price,
        })),
      });
      clearCart();
      onClose();
      addToast('Order placed successfully!', 'success');
      navigate(`/orders/${order.id}`);
    } catch {
      addToast('Checkout failed. Please try again.', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <Fragment>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 30, stiffness: 300 }}
            className="fixed right-0 top-0 z-50 flex h-full w-full max-w-md flex-col bg-surface-card border-l border-surface-border shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-surface-border px-6 py-4">
              <h2 className="text-lg font-semibold text-white">Shopping Cart</h2>
              <button
                onClick={onClose}
                className="flex h-11 w-11 items-center justify-center rounded-lg text-gray-400 hover:bg-surface-bg hover:text-white transition-colors"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-6 py-4">
              {items.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 text-gray-500">
                  <div className="flex h-20 w-20 items-center justify-center rounded-full bg-surface-bg mb-4">
                    <svg className="h-10 w-10 text-gray-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121 0 2.09-.773 2.34-1.872l1.836-8.076A1.125 1.125 0 0 0 21.768 3H6.53l-.394-1.478A1.125 1.125 0 0 0 5.05 .75H2.25M6.75 21a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
                    </svg>
                  </div>
                  <p className="text-lg font-medium">Your cart is empty</p>
                  <p className="text-sm mt-1">Browse products to get started</p>
                  <button
                    onClick={onClose}
                    className="mt-4 min-h-[44px] rounded-xl bg-nexus-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 transition-all duration-150"
                  >
                    Browse Products
                  </button>
                </div>
              ) : (
                <ul className="space-y-4">
                  {items.map((item) => (
                    <li
                      key={item.product.id}
                      className="flex gap-4 rounded-xl bg-surface-bg/50 p-4 border border-surface-border/50"
                    >
                      <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-nexus-600/30 to-nexus-800/30 text-2xl">
                        <svg className="h-6 w-6 text-nexus-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" d="m20.25 7.5-.625 10.632a2.25 2.25 0 0 1-2.247 2.118H6.622a2.25 2.25 0 0 1-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0-3-3m3 3 3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125Z" />
                        </svg>
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-medium text-white truncate">{item.product.name}</h3>
                        <p className="text-sm text-gray-400">
                          ${item.product.price.toFixed(2)} each
                        </p>
                        <div className="mt-2 flex items-center gap-2">
                          <button
                            onClick={() =>
                              item.quantity <= 1
                                ? removeItem(item.product.id)
                                : updateQuantity(item.product.id, item.quantity - 1)
                            }
                            className="flex h-9 w-9 items-center justify-center rounded-md bg-surface-border/50 text-gray-300 hover:bg-surface-border hover:text-white transition-colors"
                          >
                            &minus;
                          </button>
                          <span className="w-8 text-center text-sm font-medium text-white">
                            {item.quantity}
                          </span>
                          <button
                            onClick={() => updateQuantity(item.product.id, item.quantity + 1)}
                            className="flex h-9 w-9 items-center justify-center rounded-md bg-surface-border/50 text-gray-300 hover:bg-surface-border hover:text-white transition-colors"
                          >
                            +
                          </button>
                          <button
                            onClick={() => removeItem(item.product.id)}
                            className="ml-auto flex h-9 w-9 items-center justify-center text-red-400 hover:text-red-300 transition-colors"
                          >
                            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                            </svg>
                          </button>
                        </div>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="font-semibold text-white">
                          ${(item.product.price * item.quantity).toFixed(2)}
                        </p>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {items.length > 0 && (
              <div className="border-t border-surface-border px-6 py-4 space-y-4">
                <div className="flex items-center justify-between text-lg">
                  <span className="text-gray-400">Total</span>
                  <span className="font-bold text-white">${totalAmount().toFixed(2)}</span>
                </div>
                <button
                  onClick={handleCheckout}
                  disabled={isSubmitting}
                  className="w-full min-h-[44px] rounded-xl bg-nexus-600 py-3 font-semibold text-white hover:bg-nexus-500 active:bg-nexus-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-nexus-400 focus:ring-offset-2 focus:ring-offset-surface-card"
                >
                  {isSubmitting ? 'Placing order\u2026' : 'Checkout'}
                </button>
              </div>
            )}
          </motion.div>
        </Fragment>
      )}
    </AnimatePresence>
  );
}
