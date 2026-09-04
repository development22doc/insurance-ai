import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

export function RegisterPage() {
  const { signup, isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    username: '',
    fullName: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Redirect if already authenticated
  if (isAuthenticated && !isLoading) {
    navigate('/dashboard');
    return null;
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({
      ...prev,
      [e.target.name]: e.target.value,
    }));
    setError(null);
  };

  const validateForm = (): boolean => {
    if (!formData.username.trim()) {
      setError('Email is required');
      return false;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.username)) {
      setError('Please enter a valid email address');
      return false;
    }

    if (!formData.fullName.trim()) {
      setError('Full name is required');
      return false;
    }

    if (formData.fullName.length > 60) {
      setError('Full name must be 60 characters or less');
      return false;
    }

    if (!formData.password) {
      setError('Password is required');
      return false;
    }

    if (formData.password.length < 8) {
      setError('Password must be at least 8 characters');
      return false;
    }

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return false;
    }

    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await signup(formData.username, formData.fullName, formData.password);
      // Success - signup function handles redirect
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-blue-50 flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-5xl">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-0 items-center">

          {/* Left Column - Registration Form */}
          <div className="lg:pr-12">
            <div className="max-w-md mx-auto w-full">
              {/* Logo/Branding */}
              <div className="mb-8">
                <div className="inline-flex items-center justify-center w-14 h-14 bg-gradient-to-br from-yellow-400 to-yellow-500 rounded-xl shadow-lg mb-4">
                  <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                  </svg>
                </div>
                <h1 className="text-3xl font-bold text-gray-900">Create Account</h1>
                <p className="text-gray-600 mt-2 text-sm">Join ClaimAssist today</p>
              </div>

              {/* Error Alert */}
              {error && (
                <div className="mb-6 p-4 bg-red-50 border border-red-300 rounded-xl">
                  <div className="flex gap-3">
                    <svg className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4v.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p className="text-red-800 text-sm font-medium">{error}</p>
                  </div>
                </div>
              )}

              {/* Registration Form */}
              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label htmlFor="username" className="block text-sm font-medium text-gray-900 mb-2">
                    Email Address
                  </label>
                  <input
                    id="username"
                    name="username"
                    type="email"
                    placeholder="you@example.com"
                    value={formData.username}
                    onChange={handleChange}
                    disabled={isSubmitting}
                    required
                    autoComplete="email"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all duration-300 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                </div>

                <div>
                  <label htmlFor="fullName" className="block text-sm font-medium text-gray-900 mb-2">
                    Full Name
                  </label>
                  <input
                    id="fullName"
                    name="fullName"
                    type="text"
                    placeholder="John Doe"
                    value={formData.fullName}
                    onChange={handleChange}
                    disabled={isSubmitting}
                    required
                    autoComplete="name"
                    maxLength={60}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all duration-300 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                </div>

                <div>
                  <label htmlFor="password" className="block text-sm font-medium text-gray-900 mb-2">
                    Password
                  </label>
                  <input
                    id="password"
                    name="password"
                    type="password"
                    placeholder="••••••••"
                    value={formData.password}
                    onChange={handleChange}
                    disabled={isSubmitting}
                    required
                    autoComplete="new-password"
                    minLength={8}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all duration-300 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                  <p className="text-xs text-gray-500 mt-2">
                    Must be at least 8 characters
                  </p>
                </div>

                <div>
                  <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-900 mb-2">
                    Confirm Password
                  </label>
                  <input
                    id="confirmPassword"
                    name="confirmPassword"
                    type="password"
                    placeholder="••••••••"
                    value={formData.confirmPassword}
                    onChange={handleChange}
                    disabled={isSubmitting}
                    required
                    autoComplete="new-password"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-yellow-400 focus:border-transparent transition-all duration-300 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                </div>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full py-3 px-4 rounded-xl font-semibold transition-all duration-300 flex items-center justify-center gap-2
                    bg-gradient-to-r from-yellow-400 to-yellow-500 hover:from-yellow-500 hover:to-yellow-600
                    text-gray-900 shadow-lg hover:shadow-xl disabled:opacity-70 disabled:cursor-not-allowed"
                >
                  {isSubmitting ? (
                    <>
                      <span className="inline-block w-4 h-4 border-2 border-gray-900 border-t-transparent rounded-full animate-spin"></span>
                      Creating Account...
                    </>
                  ) : (
                    <>
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                      </svg>
                      <span>Create Account</span>
                    </>
                  )}
                </button>
              </form>

              {/* Login Link */}
              <div className="mt-6 text-center">
                <p className="text-gray-600 text-sm">
                  Already have an account?{' '}
                  <Link to="/login" className="font-medium text-yellow-600 hover:text-yellow-700 transition-colors">
                    Sign In
                  </Link>
                </p>
              </div>

              {/* Terms Note */}
              <div className="mt-6 pt-6 border-t border-gray-200">
                <p className="text-xs text-gray-500 text-center leading-relaxed">
                  By creating an account, you agree to our Terms of Service and Privacy Policy
                </p>
              </div>

              {/* Footer Links */}
              <div className="mt-4 flex justify-center items-center gap-3 text-xs">
                <Link to="/about" className="text-gray-600 hover:text-gray-900 transition-colors">
                  About
                </Link>
                <span className="text-gray-300">•</span>
                <Link to="/contact" className="text-gray-600 hover:text-gray-900 transition-colors">
                  Contact
                </Link>
                <span className="text-gray-300">•</span>
                <Link to="/products" className="text-gray-600 hover:text-gray-900 transition-colors">
                  Products
                </Link>
              </div>
            </div>
          </div>

          {/* Right Column - Illustration */}
          <div className="hidden lg:flex flex-col items-center justify-center">
            <div className="relative w-full max-w-sm">
              {/* Background shape */}
              <div className="absolute inset-0 bg-gradient-to-br from-emerald-100 to-blue-100 rounded-3xl opacity-30 blur-3xl"></div>

              {/* Illustration */}
              <svg
                className="relative w-full h-auto"
                viewBox="0 0 400 400"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                {/* Folder and document icon */}
                <defs>
                  <linearGradient id="registerGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" style={{ stopColor: '#F9B900', stopOpacity: 0.2 }} />
                    <stop offset="100%" style={{ stopColor: '#10B981', stopOpacity: 0.2 }} />
                  </linearGradient>
                </defs>

                {/* Folder */}
                <path
                  d="M80 120L120 80H280V280H80V120Z"
                  fill="url(#registerGradient)"
                  stroke="#10B981"
                  strokeWidth="2"
                />

                {/* Folder tab */}
                <path
                  d="M80 120L120 80H180L160 120"
                  fill="none"
                  stroke="#F9B900"
                  strokeWidth="2"
                />

                {/* Document lines */}
                <line x1="120" y1="150" x2="240" y2="150" stroke="#0369A1" strokeWidth="2" strokeLinecap="round" />
                <line x1="120" y1="180" x2="240" y2="180" stroke="#0369A1" strokeWidth="2" strokeLinecap="round" />
                <line x1="120" y1="210" x2="200" y2="210" stroke="#0369A1" strokeWidth="2" strokeLinecap="round" />

                {/* Checkmark circle */}
                <circle
                  cx="240"
                  cy="240"
                  r="45"
                  fill="#10B981"
                  opacity="0.1"
                />
                <path
                  d="M225 240L235 250L255 230"
                  stroke="#10B981"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  fill="none"
                />
              </svg>

              {/* Text below illustration */}
              <div className="text-center mt-8">
                <h2 className="text-2xl font-bold text-gray-900 mb-2">
                  Get Started Today
                </h2>
                <p className="text-gray-600">
                  Create your account to access your policies and file claims instantly.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
