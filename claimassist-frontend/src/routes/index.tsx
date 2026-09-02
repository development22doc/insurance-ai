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

import { DashboardPage } from '../pages/DashboardPage';
import { PoliciesListPage } from '../pages/PoliciesListPage';
import { PolicyDetailsPage } from '../pages/PolicyDetailsPage';
import { ClaimsListPage } from '../pages/ClaimsListPage';
import { ClaimDetailsPage } from '../pages/ClaimDetailsPage';
import ClaimsNewPage from '../pages/ClaimsNewPage';
import { OperationsDashboardPage } from '../pages/OperationsDashboardPage';
import { OperationsClaimsPage } from '../pages/OperationsClaimsPage';
import { OperationsClaimWorkspacePage } from '../pages/OperationsClaimWorkspacePage';
const NewClaimPage = () => <ClaimsNewPage />;
import DocumentsPage from '../pages/DocumentsPage';
const NotificationsPage = () => <div className="p-8"><h1 className="text-2xl font-bold">Notifications</h1></div>;
import ProfilePage from '../pages/ProfilePage';

const OperationsClaimDetailsPage = () => <OperationsClaimWorkspacePage />;

/* Admin pages (preview-only imports) */
import { AdminDashboardPage } from '../pages/AdminDashboardPage';
import { AdminUsersPage } from '../pages/AdminUsersPage';
import { AdminRbacPage } from '../pages/AdminRbacPage';
import { AdminProductsPage } from '../pages/AdminProductsPage';
import { AdminAiPage } from '../pages/AdminAiPage';
import { AdminRiskPage } from '../pages/AdminRiskPage';
import { AdminAuditPage } from '../pages/AdminAuditPage';
import { AdminDocumentsPage } from '../pages/AdminDocumentsPage';

/* Operations preview pages */
import { OperationsAssignmentsPage } from '../pages/OperationsAssignmentsPage';
import { OperationsSurveyorsPage } from '../pages/OperationsSurveyorsPage';
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
      { path: 'customer/auth/callback', element: <CallbackPage /> },
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
      { index: true, element: <DashboardPage /> },
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
      { index: true, element: <PoliciesListPage /> },
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
      { index: true, element: <ClaimsListPage /> },
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
      { index: true, element: <OperationsDashboardPage /> },
      { path: 'claims', element: <OperationsClaimsPage /> },
      { path: 'claims/:id', element: <OperationsClaimDetailsPage /> },
      { path: 'assignments', element: <OperationsAssignmentsPage /> },
      { path: 'surveyors', element: <OperationsSurveyorsPage /> },
    ],
  },

  {
    path: '/admin',
    element: (
      <ProtectedRoute requiredRoles={['ADMIN','SUPPORT']}>
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: 'users', element: <AdminUsersPage /> },
      { path: 'rbac', element: <AdminRbacPage /> },
      { path: 'products', element: <AdminProductsPage /> },
      { path: 'ai', element: <AdminAiPage /> },
      { path: 'risk', element: <AdminRiskPage /> },
      { path: 'audit', element: <AdminAuditPage /> },
      { path: 'documents', element: <AdminDocumentsPage /> },
    ],
  },

  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
]);
