import { AnimatePresence, motion } from 'framer-motion';
import { useToastStore, ToastType } from '@/store/toastStore';

const ICON: Record<ToastType, string> = {
  success: '\u2713',
  error: '\u2717',
  info: '\u2139',
};

const STYLE: Record<ToastType, string> = {
  success: 'border-green-500/30 bg-green-500/10 text-green-300',
  error: 'border-red-500/30 bg-red-500/10 text-red-300',
  info: 'border-nexus-500/30 bg-nexus-500/10 text-nexus-300',
};

export default function ToastContainer() {
  const { toasts, removeToast } = useToastStore();

  return (
    <div className="fixed bottom-6 right-6 z-[100] flex flex-col gap-3 max-w-sm">
      <AnimatePresence>
        {toasts.map((toast) => (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.95 }}
            transition={{ duration: 0.2 }}
            className={`flex items-center gap-3 rounded-xl border px-4 py-3 shadow-lg backdrop-blur-sm ${STYLE[toast.type]}`}
          >
            <span className="text-lg font-bold shrink-0">{ICON[toast.type]}</span>
            <p className="text-sm font-medium flex-1">{toast.message}</p>
            <button
              onClick={() => removeToast(toast.id)}
              className="shrink-0 rounded-md p-1 opacity-60 hover:opacity-100 transition-opacity"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
