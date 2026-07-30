import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import AdminLayout from './components/AdminLayout';
import ProtectedRoute from './components/ProtectedRoute';
import RequireAuth from './components/RequireAuth';

import Home from './pages/Home';
import Products from './pages/Products';
import ProductDetails from './pages/ProductDetails';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Login from './pages/Login';
import Register from './pages/Register';
import Orders from './pages/Orders';
import OrderDetails from './pages/OrderDetails';
import NotFound from './pages/NotFound';

import AdminDashboard from './pages/admin/AdminDashboard';
import AdminProducts from './pages/admin/AdminProducts';
import AdminUsers from './pages/admin/AdminUsers';
import AdminOrders from './pages/admin/AdminOrders';
import AdminSuppliers from './pages/admin/AdminSuppliers';
import AdminPurchases from './pages/admin/AdminPurchases';
import AdminAccounting from './pages/admin/AdminAccounting';
import AdminAuditLog from './pages/admin/AdminAuditLog';
import AdminSettings from './pages/admin/AdminSettings';
import AdminLoginEvents from './pages/admin/AdminLoginEvents';

export default function App() {
  return (
    <Routes>
      {/* Standalone auth pages — the only thing an unauthenticated visitor can reach */}
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* Everything else is private: requires an authenticated session */}
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/products" element={<Products />} />
          <Route path="/products/:id" element={<ProductDetails />} />
          <Route path="/cart" element={<Cart />} />

        {/* Authenticated users */}
        <Route
          path="/checkout"
          element={
            <ProtectedRoute>
              <Checkout />
            </ProtectedRoute>
          }
        />
        <Route
          path="/orders"
          element={
            <ProtectedRoute>
              <Orders />
            </ProtectedRoute>
          }
        />
        <Route
          path="/orders/:id"
          element={
            <ProtectedRoute>
              <OrderDetails />
            </ProtectedRoute>
          }
        />

        {/* Admin — one guarded layout route so the section navigation is drawn
            once and stays on screen for every admin page. */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute adminOnly>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<AdminDashboard />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="users" element={<AdminUsers />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="suppliers" element={<AdminSuppliers />} />
          <Route path="purchases" element={<AdminPurchases />} />
          <Route path="accounting" element={<AdminAccounting />} />
          <Route path="audit" element={<AdminAuditLog />} />
          <Route path="settings" element={<AdminSettings />} />
          <Route path="login-events" element={<AdminLoginEvents />} />
        </Route>

          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
