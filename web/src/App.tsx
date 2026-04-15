import { Routes, Route } from 'react-router-dom';
import ProductCatalog from './pages/ProductCatalog';
import OrderHistory from './pages/OrderHistory';
import OrderTracking from './pages/OrderTracking';
import Layout from './components/Layout';
import ToastContainer from './components/ToastContainer';

function App() {
  return (
    <>
      <Layout>
        <Routes>
          <Route path="/" element={<ProductCatalog />} />
          <Route path="/orders" element={<OrderHistory />} />
          <Route path="/orders/:id" element={<OrderTracking />} />
        </Routes>
      </Layout>
      <ToastContainer />
    </>
  );
}

export default App;
