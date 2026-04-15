import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { fetchProducts } from '@/lib/api';
import { useCartStore } from '@/store/cartStore';
import { useToastStore } from '@/store/toastStore';
import { Product } from '@/types';

const GRADIENTS = [
  'from-blue-600/20 to-purple-600/20',
  'from-emerald-600/20 to-teal-600/20',
  'from-orange-600/20 to-red-600/20',
  'from-pink-600/20 to-rose-600/20',
  'from-cyan-600/20 to-blue-600/20',
  'from-violet-600/20 to-indigo-600/20',
];

const PRODUCT_EMOJIS = ['\uD83D\uDCBB', '\uD83D\uDCF1', '\uD83C\uDFA7', '\u2328\uFE0F', '\uD83D\uDDA5\uFE0F', '\uD83C\uDFAE', '\uD83D\uDCF7', '\uD83D\uDDA8\uFE0F', '\uD83D\uDCA1', '\uD83D\uDD0C', '\uD83D\uDD0B', '\uD83D\uDCC0'];

function getEmoji(index: number) {
  return PRODUCT_EMOJIS[index % PRODUCT_EMOJIS.length];
}

function getGradient(index: number) {
  return GRADIENTS[index % GRADIENTS.length];
}

function StockBadge({ quantity }: { quantity: number }) {
  if (quantity === 0) {
    return (
      <span className="inline-flex items-center rounded-full bg-red-500/10 px-2.5 py-0.5 text-xs font-medium text-red-400 ring-1 ring-inset ring-red-500/20">
        Out of stock
      </span>
    );
  }
  if (quantity <= 10) {
    return (
      <span className="inline-flex items-center rounded-full bg-yellow-500/10 px-2.5 py-0.5 text-xs font-medium text-yellow-400 ring-1 ring-inset ring-yellow-500/20">
        {quantity} left
      </span>
    );
  }
  return (
    <span className="inline-flex items-center rounded-full bg-green-500/10 px-2.5 py-0.5 text-xs font-medium text-green-400 ring-1 ring-inset ring-green-500/20">
      In stock
    </span>
  );
}

function SkeletonCard() {
  return (
    <div className="flex flex-col rounded-2xl border border-surface-border bg-surface-card overflow-hidden animate-pulse">
      <div className="h-44 bg-surface-border/30" />
      <div className="flex flex-1 flex-col p-5 space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div className="h-5 w-32 rounded bg-surface-border/50" />
          <div className="h-5 w-16 rounded-full bg-surface-border/50" />
        </div>
        <div className="space-y-2 flex-1">
          <div className="h-3 w-full rounded bg-surface-border/30" />
          <div className="h-3 w-3/4 rounded bg-surface-border/30" />
        </div>
        <div className="flex items-center justify-between pt-2">
          <div className="h-7 w-20 rounded bg-surface-border/50" />
          <div className="h-10 w-28 rounded-xl bg-surface-border/50" />
        </div>
      </div>
    </div>
  );
}

const containerVariants = {
  hidden: {},
  show: {
    transition: {
      staggerChildren: 0.06,
    },
  },
};

const cardVariants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
};

export default function ProductCatalog() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const addItem = useCartStore((s) => s.addItem);
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    fetchProducts()
      .then(setProducts)
      .catch(() => setError('Failed to load products. Is the API running?'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div>
        <div className="mb-8">
          <div className="h-8 w-40 rounded bg-surface-border/50 animate-pulse" />
          <div className="mt-2 h-4 w-72 rounded bg-surface-border/30 animate-pulse" />
        </div>
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <SkeletonCard key={i} />
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

  if (products.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-surface-card mb-4">
          <svg className="h-10 w-10 text-gray-500" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="m20.25 7.5-.625 10.632a2.25 2.25 0 0 1-2.247 2.118H6.622a2.25 2.25 0 0 1-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0-3-3m3 3 3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125Z" />
          </svg>
        </div>
        <p className="text-lg font-medium">No products available</p>
        <p className="text-sm mt-1 text-gray-500">Check back soon for new items</p>
      </div>
    );
  }

  const handleAddToCart = (product: Product) => {
    addItem(product);
    addToast(`${product.name} added to cart`, 'success');
  };

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white">Products</h1>
        <p className="mt-1 text-gray-400">Browse our catalog and add items to your cart</p>
      </div>

      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
      >
        {products.map((product, i) => (
          <motion.div
            key={product.id}
            variants={cardVariants}
            className="group flex flex-col rounded-2xl border border-surface-border bg-surface-card overflow-hidden hover:border-surface-border-light transition-all duration-150"
          >
            <div
              className={`flex h-44 items-center justify-center bg-gradient-to-br ${getGradient(i)}`}
            >
              <span className="text-6xl drop-shadow-lg group-hover:scale-110 transition-transform duration-300">
                {getEmoji(i)}
              </span>
            </div>
            <div className="flex flex-1 flex-col p-5">
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-semibold text-white">{product.name}</h3>
                <StockBadge quantity={product.stockQuantity} />
              </div>
              <p className="mt-2 text-sm text-gray-400 line-clamp-2 flex-1">
                {product.description}
              </p>
              <div className="mt-4 flex items-center justify-between">
                <span className="text-2xl font-bold text-white">
                  ${product.price.toFixed(2)}
                </span>
                <button
                  onClick={() => handleAddToCart(product)}
                  disabled={product.stockQuantity === 0}
                  className="min-h-[44px] rounded-xl bg-nexus-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-nexus-500 active:bg-nexus-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-nexus-400 focus:ring-offset-2 focus:ring-offset-surface-card"
                >
                  Add to Cart
                </button>
              </div>
            </div>
          </motion.div>
        ))}
      </motion.div>
    </div>
  );
}
