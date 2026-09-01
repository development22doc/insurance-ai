import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { PublicLayout } from '../components/layout';
import { CustomerLayout } from '../components/layout';
import { OperationsLayout } from '../components/layout';
import { AdminLayout } from '../components/layout';
import { ProtectedRoute } from '../components/auth';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { CallbackPage } from '../pages/auth/CallbackPage';
import { UnauthorizedPage } from '../pages/auth/UnauthorizedPage';

/* eslint-disable react/only-export-components */
import { HomePage } from '../pages/HomePage';
import { ProductsPage } from '../pages/ProductsPage';
import { ClaimsPublicPage } from '../pages/ClaimsPublicPage';
import { AboutPage } from '../pages/AboutPage';
import { ContactPage } from '../pages/ContactPage';

const CustomerDashboard = () => <div className="p-8"><h1 className="text-2xl font-bold">Customer Dashboard</h1></div>;
const PoliciesPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Policies</h1></div>;
const PolicyDetailsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Policy Details</h1></div>;
const CustomerClaimsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">My Claims</h1></div>;
const ClaimDetailsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Claim Details</h1></div>;
const NewClaimPage = () => <div className="p-8"><h1 className="text-2xl font-bold">File New Claim</h1></div>;
const DocumentsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Documents</h1></div>;
const NotificationsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Notifications</h1></div>;
const ProfilePage = () => <div className="p-8"><h1 className="text-2xl font-bold">Profile</h1></div>;

const OperationsDashboard = () => <div className="p-8"><h1 className="text-2xl font-bold">Operations Dashboard</h1></div>;
const OperationsClaimsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Operations Claims</h1></div>;
const OperationsClaimDetailsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Operations Claim Details</h1></div>;

const AdminDashboard = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin Dashboard</h1></div>;
const AdminUsersPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin Users</h1></div>;
const AdminRbacPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin RBAC</h1></div>;
const AdminProductsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin Products</h1></div>;
const AdminAiPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin AI Configuration</h1></div>;
const AdminRiskPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin Fraud/Risk</h1></div>;
const AdminAuditPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Admin Audit</h1></div>;
/* eslint-enable react/only-export-components */

export const router = createBrowserRouter([
  {
    path: '/',
    element: <PublicLayout><Outlet /></PublicLayout>,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'products', element: <ProductsPage /> },
      { path: 'claims', element: <ClaimsPublicPage /> },
      { path: 'about', element: <AboutPage /> },
      { path: 'contact', element: <ContactPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'callback', element: <CallbackPage /> },
      { path: 'unauthorized', element: <UnauthorizedPage /> },
    ],
  },
  {
    path: '/dashboard',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <CustomerDashboard /> },
    ],
  },
  {
    path: '/policies',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <PoliciesPage /> },
      { path: ':id', element: <PolicyDetailsPage /> },
    ],
  },
  {
    path: '/claims',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <CustomerClaimsPage /> },
      { path: 'new', element: <NewClaimPage /> },
      { path: ':id', element: <ClaimDetailsPage /> },
    ],
  },
  {
    path: '/documents',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DocumentsPage /> },
    ],
  },
  {
    path: '/notifications',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <NotificationsPage /> },
    ],
  },
  {
    path: '/profile',
    element: (
      <ProtectedRoute requiredRoles={['CUSTOMER']}>
        <CustomerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <ProfilePage /> },
    ],
  },
  {
    path: '/operations',
    element: (
      <ProtectedRoute requiredRoles={['ADJUSTER', 'AUDITOR']}>
        <OperationsLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <OperationsDashboard /> },
      { path: 'claims', element: <OperationsClaimsPage /> },
      { path: 'claims/:id', element: <OperationsClaimDetailsPage /> },
    ],
  },
  {
    path: '/admin',
    element: (
      <ProtectedRoute>
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <AdminDashboard /> },
      { path: 'users', element: <AdminUsersPage /> },
      { path: 'rbac', element: <AdminRbacPage /> },
      { path: 'products', element: <AdminProductsPage /> },
      { path: 'ai', element: <AdminAiPage /> },
      { path: 'risk', element: <AdminRiskPage /> },
      { path: 'audit', element: <AdminAuditPage /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]);
