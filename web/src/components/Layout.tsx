import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useCartStore } from '@/store/cartStore';
import CartSlideOver from './CartSlideOver';

interface LayoutProps {
  children: ReactNode;
}

const NAV_LINKS = [
  { to: '/', label: 'Products', match: (p: string) => p === '/' },
  { to: '/orders', label: 'Orders', match: (p: string) => p.startsWith('/orders') },
];

export default function Layout({ children }: LayoutProps) {
  const [cartOpen, setCartOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const totalItems = useCartStore((s) => s.totalItems());

  return (
    <div className="min-h-screen">
      <nav className="sticky top-0 z-30 border-b border-gray-800 bg-gray-950/80 backdrop-blur-md">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between">
            <Link to="/" className="text-xl font-bold text-nexus-400">
              ⚡ Nexus
            </Link>

            {/* Desktop nav */}
            <div className="hidden sm:flex items-center gap-6">
              {NAV_LINKS.map((link) => {
                const active = link.match(location.pathname);
                return (
                  <Link
                    key={link.to}
                    to={link.to}
                    className={`text-sm font-medium transition-colors ${
                      active
                        ? 'text-white'
                        : 'text-gray-400 hover:text-white'
                    }`}
                  >
                    {link.label}
                    {active && (
                      <motion.div
                        layoutId="nav-underline"
                        className="mt-1 h-0.5 rounded-full bg-nexus-500"
                      />
                    )}
                  </Link>
                );
              })}

              <button
                onClick={() => setCartOpen(true)}
                className="relative ml-2 rounded-lg p-2 text-gray-400 hover:bg-gray-800 hover:text-white transition-colors"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121 0 2.09-.773 2.34-1.872l1.836-8.076A1.125 1.125 0 0 0 21.768 3H6.53l-.394-1.478A1.125 1.125 0 0 0 5.05 .75H2.25M6.75 21a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
                </svg>
                <AnimatePresence>
                  {totalItems > 0 && (
                    <motion.span
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      exit={{ scale: 0 }}
                      className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-nexus-500 text-[10px] font-bold text-white"
                    >
                      {totalItems > 99 ? '99+' : totalItems}
                    </motion.span>
                  )}
                </AnimatePresence>
              </button>
            </div>

            {/* Mobile: cart + hamburger */}
            <div className="flex items-center gap-2 sm:hidden">
              <button
                onClick={() => setCartOpen(true)}
                className="relative rounded-lg p-2 text-gray-400 hover:bg-gray-800 hover:text-white transition-colors"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121 0 2.09-.773 2.34-1.872l1.836-8.076A1.125 1.125 0 0 0 21.768 3H6.53l-.394-1.478A1.125 1.125 0 0 0 5.05 .75H2.25M6.75 21a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
                </svg>
                {totalItems > 0 && (
                  <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-nexus-500 text-[10px] font-bold text-white">
                    {totalItems > 99 ? '99+' : totalItems}
                  </span>
                )}
              </button>
              <button
                onClick={() => setMobileMenuOpen((v) => !v)}
                className="rounded-lg p-2 text-gray-400 hover:bg-gray-800 hover:text-white transition-colors"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  {mobileMenuOpen ? (
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  ) : (
                    <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                  )}
                </svg>
              </button>
            </div>
          </div>
        </div>

        {/* Mobile menu */}
        <AnimatePresence>
          {mobileMenuOpen && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="overflow-hidden border-t border-gray-800 sm:hidden"
            >
              <div className="px-4 py-3 space-y-1">
                {NAV_LINKS.map((link) => {
                  const active = link.match(location.pathname);
                  return (
                    <Link
                      key={link.to}
                      to={link.to}
                      onClick={() => setMobileMenuOpen(false)}
                      className={`block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                        active
                          ? 'bg-gray-800 text-white'
                          : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'
                      }`}
                    >
                      {link.label}
                    </Link>
                  );
                })}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </nav>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>

      <CartSlideOver open={cartOpen} onClose={() => setCartOpen(false)} />
    </div>
  );
}
