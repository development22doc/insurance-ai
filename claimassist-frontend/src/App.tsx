import { AuthProvider, useAuth } from '@/context/AuthContext';
import { useRouter, navigate } from '@/lib/router';
import { Navbar } from '@/components/layout/Navbar';
import { Footer } from '@/components/layout/Footer';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { ComingSoon } from '@/components/shared/ComingSoon';
import { FileText } from 'lucide-react';

import { HomePage } from '@/pages/HomePage';
import { ProductsPage } from '@/pages/ProductsPage';
import { ClaimsPage } from '@/pages/ClaimsPage';
import { AboutPage } from '@/pages/AboutPage';
import { ContactPage } from '@/pages/ContactPage';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { AuthCallbackPage } from '@/pages/AuthCallbackPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PoliciesPage } from '@/pages/PoliciesPage';
import { PolicyDetailPage } from '@/pages/PolicyDetailPage';
import { ClaimsListPage } from '@/pages/ClaimsListPage';
import { ClaimSubmissionPage } from '@/pages/ClaimSubmissionPage';
import { ClaimDetailPage } from '@/pages/ClaimDetailPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { OperationsPage } from '@/pages/OperationsPage';

const PUBLIC_ROUTES = new Set(['/', '/products', '/claims', '/about', '/contact', '/login', '/register', '/callback']);

function AppContent() {
  const { route } = useRouter();
  const { isAuthenticated, loading } = useAuth();

  if (loading) return <FullPageSpinner />;

  const path = route.path;

  // Auth pages: redirect if already logged in
  if (path === '/login' || path === '/register') {
    if (isAuthenticated) { navigate('/dashboard'); return null; }
  }

  // Protected routes
  const isProtected = !PUBLIC_ROUTES.has(path);
  if (isProtected && !isAuthenticated) {
    navigate('/login');
    return null;
  }

  // Render page
  const renderPage = () => {
    switch (path) {
      case '/': return <HomePage />;
      case '/products': return <ProductsPage />;
      case '/claims': return <ClaimsPage />;
      case '/about': return <AboutPage />;
      case '/contact': return <ContactPage />;
      case '/login': return <LoginPage />;
      case '/register': return <RegisterPage />;
      case '/callback': return <AuthCallbackPage />;
      case '/dashboard': return <DashboardPage />;
      case '/policies': return <PoliciesPage />;
      case '/policy/:id': return <PolicyDetailPage policyId={route.params.id} />;
      case '/claims-list': return <ClaimsListPage />;
      case '/file-claim': return <ClaimSubmissionPage />;
      case '/claim/:id': return <ClaimDetailPage claimId={route.params.id} />;
      case '/profile': return <ProfilePage />;
      case '/operations': return <OperationsPage />;
      default: return (
        <ComingSoon
          title="Page Not Found"
          description="The page you're looking for doesn't exist or may have moved."
          icon={<FileText className="w-8 h-8 text-blue-500" />}
          actionLabel="Back to Home"
          actionPath="/"
        />
      );
    }
  };

  // Auth pages have their own full-screen layout (no navbar/footer)
  if (path === '/login' || path === '/register' || path === '/callback') {
    return renderPage();
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar />
      <main className="flex-1">
        {renderPage()}
      </main>
      <Footer />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
