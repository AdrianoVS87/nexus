import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { fetchProducts } from '@/lib/api';
import { useCartStore } from '@/store/cartStore';
import { Product } from '@/types';

const PRODUCT_EMOJIS = ['💻', '📱', '🎧', '⌨️', '🖥️', '🎮', '📷', '🖨️', '💡', '🔌', '🔋', '📀'];
const GRADIENTS = [
  'from-blue-600/20 to-purple-600/20',
  'from-emerald-600/20 to-teal-600/20',
  'from-orange-600/20 to-red-600/20',
  'from-pink-600/20 to-rose-600/20',
  'from-cyan-600/20 to-blue-600/20',
  'from-violet-600/20 to-indigo-600/20',
];

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
      In stock ({quantity})
    </span>
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

  useEffect(() => {
    fetchProducts()
      .then(setProducts)
      .catch(() => setError('Failed to load products. Is the API running?'))
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

  if (products.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-gray-400">
        <span className="text-5xl mb-4">📦</span>
        <p className="text-lg font-medium">No products available</p>
      </div>
    );
  }

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
        className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
      >
        {products.map((product, i) => (
          <motion.div
            key={product.id}
            variants={cardVariants}
            className="group flex flex-col rounded-2xl border border-gray-800 bg-gray-900 overflow-hidden hover:border-gray-700 transition-colors"
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
                  onClick={() => addItem(product)}
                  disabled={product.stockQuantity === 0}
                  className="rounded-xl bg-nexus-600 px-4 py-2 text-sm font-semibold text-white hover:bg-nexus-500 active:bg-nexus-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-nexus-400 focus:ring-offset-2 focus:ring-offset-gray-900"
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
