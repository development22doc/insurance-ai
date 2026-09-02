import { useAuth } from '../../contexts/AuthContext';
import { Button } from '../../components/ui';

export function UnauthorizedPage() {
  const { logout, user } = useAuth();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
      <div className="max-w-md w-full">
        <div className="bg-white rounded-2xl shadow-xl p-8 text-center">
          {/* Icon */}
          <div className="inline-flex items-center justify-center w-20 h-20 bg-amber-100 rounded-full mb-6">
            <svg className="w-10 h-10 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>

          {/* Title */}
          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            Access Denied
          </h1>

          {/* Message */}
          <p className="text-gray-600 mb-6">
            You don't have permission to access this page.
            {user && user.roles.length > 0 && (
              <span className="block mt-2 text-sm">
                Your current role(s): {user.roles.join(', ')}
              </span>
            )}
          </p>

          {/* Actions */}
          <div className="space-y-3">
            <Button
              onClick={() => window.history.back()}
              variant="secondary"
              className="w-full"
            >
              Go Back
            </Button>
            <Button
              onClick={logout}
              variant="ghost"
              className="w-full"
            >
              Sign Out
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
