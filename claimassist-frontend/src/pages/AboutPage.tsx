import React from 'react';

export const AboutPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gradient-to-b from-white to-gray-50">
      {/* Hero Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-block mb-4 px-4 py-2 bg-blue-50 rounded-full border border-blue-200">
            <p className="text-sm font-medium text-blue-900">About ClaimAssist</p>
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold text-gray-900 mt-4">
            Insurance Claims, Simplified
          </h1>
          <p className="text-xl text-gray-600 mt-6 max-w-2xl mx-auto">
            ClaimAssist builds focused tools to simplify insurance claims for customers and operations teams alike.
          </p>
        </div>
      </section>

      {/* Mission Section */}
      <section className="px-4 py-12 sm:px-6 lg:px-8">
        <div className="max-w-5xl mx-auto">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <div className="bg-white rounded-2xl border border-gray-200 p-8 hover:shadow-lg transition-shadow duration-300">
              <div className="w-14 h-14 rounded-lg bg-blue-100 flex items-center justify-center mb-4">
                <svg className="w-7 h-7 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-3">Our Mission</h2>
              <p className="text-gray-600 leading-relaxed">
                Make claims fair, transparent, and fast through better workflows and applied machine learning where it helps reviewers.
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-gray-200 p-8 hover:shadow-lg transition-shadow duration-300">
              <div className="w-14 h-14 rounded-lg bg-emerald-100 flex items-center justify-center mb-4">
                <svg className="w-7 h-7 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-3">What We Build</h2>
              <p className="text-gray-600 leading-relaxed">
                A claims-first platform: filing, secure document management, AI-assisted extraction, role-based operations, and clear tracking.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* AI Section */}
      <section className="px-4 py-12 sm:px-6 lg:px-8 bg-gradient-to-r from-blue-50 to-indigo-50">
        <div className="max-w-4xl mx-auto">
          <div className="bg-white rounded-2xl border border-gray-200 p-8 sm:p-12">
            <div className="flex items-start gap-4">
              <div className="flex-shrink-0">
                <div className="w-14 h-14 rounded-lg bg-yellow-100 flex items-center justify-center">
                  <svg className="w-7 h-7 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                  </svg>
                </div>
              </div>
              <div className="flex-1">
                <h2 className="text-2xl font-bold text-gray-900 mb-3">AI in ClaimAssist</h2>
                <p className="text-gray-600 leading-relaxed">
                  We use AI to assist with information extraction and prioritization — not to replace human adjusters. Final decisions and reviews remain with trained staff. Our machine learning helps us understand patterns and support faster, fairer claim handling.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Values Section */}
      <section className="px-4 py-12 sm:px-6 lg:px-8">
        <div className="max-w-5xl mx-auto">
          <h2 className="text-3xl font-bold text-gray-900 text-center mb-12">Our Values</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[
              {
                icon: '🛡️',
                title: 'Transparency',
                description: 'Clear communication about your claim status and decisions at every step.',
              },
              {
                icon: '⚡',
                title: 'Speed',
                description: 'Fast processing without sacrificing accuracy or fairness.',
              },
              {
                icon: '✓',
                title: 'Fairness',
                description: 'Consistent, equitable treatment for all customers and claims.',
              },
            ].map((value, idx) => (
              <div key={idx} className="text-center">
                <div className="text-5xl mb-4">{value.icon}</div>
                <h3 className="text-lg font-bold text-gray-900 mb-2">{value.title}</h3>
                <p className="text-gray-600">{value.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8 bg-gradient-to-r from-blue-600 to-blue-800">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-bold text-white mb-4">
            Ready to experience ClaimAssist?
          </h2>
          <p className="text-blue-100 mb-8">
            Start managing your insurance claims with transparency and speed.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <a href="/register" className="px-8 py-3 bg-yellow-400 hover:bg-yellow-500 text-gray-900 font-semibold rounded-lg transition-colors duration-300">
              Create Account
            </a>
            <a href="/login" className="px-8 py-3 border-2 border-white text-white hover:bg-white hover:text-blue-700 font-semibold rounded-lg transition-colors duration-300">
              Sign In
            </a>
          </div>
        </div>
      </section>
    </div>
  );
};
