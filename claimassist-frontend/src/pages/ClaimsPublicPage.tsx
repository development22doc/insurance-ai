import React from 'react';
import { Link } from 'react-router-dom';

export const ClaimsPublicPage: React.FC = () => {
  const features = [
    {
      icon: '📋',
      title: 'File a Claim',
      description: 'Start a claim with a guided form. Upload photos or documents for faster processing.',
    },
    {
      icon: '🤖',
      title: 'AI-Assisted Review',
      description: 'Our AI helps extract details from documents. Human adjusters make final decisions.',
    },
    {
      icon: '📊',
      title: 'Track Progress',
      description: 'See who is working on your claim, what documents are needed, and current status.',
    },
    {
      icon: '🔒',
      title: 'Security & Privacy',
      description: 'Documents are transmitted over TLS and stored securely. Access is controlled by authentication.',
    },
  ];

  const processSteps = [
    { number: '1', title: 'Submit', description: 'File your claim with incident details and documentation' },
    { number: '2', title: 'Review', description: 'AI extracts key information; adjusters review thoroughly' },
    { number: '3', title: 'Decision', description: 'Receive approval status and payment information' },
    { number: '4', title: 'Closure', description: 'Track payment and complete claim resolution' },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-b from-white to-gray-50">
      {/* Hero Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-block mb-4 px-4 py-2 bg-blue-50 rounded-full border border-blue-200">
            <p className="text-sm font-medium text-blue-900">Claims-First Experience</p>
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold text-gray-900 mt-4">
            Submitting a Claim
          </h1>
          <p className="text-xl text-gray-600 mt-6 max-w-2xl mx-auto">
            Learn how ClaimAssist helps you through every step of the claims journey.
          </p>
        </div>
      </section>

      {/* Features Grid */}
      <section className="px-4 py-12 sm:px-6 lg:px-8">
        <div className="max-w-7xl mx-auto">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {features.map((feature, idx) => (
              <div
                key={idx}
                className="bg-white rounded-2xl border border-gray-200 p-8 hover:shadow-lg transition-shadow duration-300"
              >
                <div className="w-16 h-16 rounded-lg bg-blue-100 flex items-center justify-center mb-4">
                  {feature.icon === '📋' && (
                    <svg className="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                  )}
                  {feature.icon === '🤖' && (
                    <svg className="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                  )}
                  {feature.icon === '📊' && (
                    <svg className="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                  )}
                  {feature.icon === '🔒' && (
                    <svg className="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                  )}
                </div>
                <h3 className="text-lg font-bold text-gray-900 mb-2">{feature.title}</h3>
                <p className="text-gray-600 leading-relaxed">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Process Steps */}
      <section className="px-4 py-12 sm:px-6 lg:px-8 bg-gradient-to-r from-blue-50 to-indigo-50">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-gray-900 mb-4">The Claims Process</h2>
            <p className="text-gray-600">How we handle your claim from submission to completion</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {processSteps.map((step, idx) => (
              <div key={idx} className="relative">
                <div className="bg-white rounded-xl border border-gray-200 p-6 text-center">
                  <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-yellow-100 text-yellow-600 font-bold mb-4">
                    {step.number}
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">{step.title}</h3>
                  <p className="text-sm text-gray-600">{step.description}</p>
                </div>
                {idx < processSteps.length - 1 && (
                  <div className="hidden md:block absolute top-8 -right-8 w-8 h-0.5 bg-gradient-to-r from-yellow-400 to-transparent" />
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Key Benefits */}
      <section className="px-4 py-12 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto">
          <div className="bg-white rounded-2xl border border-gray-200 p-12">
            <h2 className="text-3xl font-bold text-gray-900 mb-8 text-center">Why ClaimAssist?</h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {[
                'Fast, transparent claim processing',
                'AI-assisted document analysis',
                'Real-time status tracking',
                'Secure data handling',
                'Expert adjuster review',
                'Multi-channel communication',
              ].map((benefit, idx) => (
                <div key={idx} className="flex items-start gap-4">
                  <div className="flex-shrink-0 w-6 h-6 rounded-full bg-emerald-100 flex items-center justify-center mt-0.5">
                    <svg className="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                  <p className="text-gray-700 font-medium">{benefit}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8 bg-gradient-to-r from-blue-600 to-blue-800">
        <div className="max-w-3xl mx-auto text-center">
          <h2 className="text-3xl sm:text-4xl font-bold text-white mb-4">
            Ready to File a Claim?
          </h2>
          <p className="text-blue-100 mb-8 text-lg">
            Sign in to your account or create one to get started with your claim today.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Link
              to="/login"
              className="px-8 py-3 bg-yellow-400 hover:bg-yellow-500 text-gray-900 font-semibold rounded-lg transition-colors duration-300"
            >
              Sign In
            </Link>
            <Link
              to="/register"
              className="px-8 py-3 border-2 border-white text-white hover:bg-white hover:text-blue-700 font-semibold rounded-lg transition-colors duration-300"
            >
              Create Account
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
};
