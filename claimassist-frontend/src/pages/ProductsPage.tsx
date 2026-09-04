import React from 'react';
import { Link } from 'react-router-dom';
import { CarIcon, HealthIcon, TravelIcon, CommercialIcon, CheckIcon } from '../components/icons/InsuranceIcons';

export const ProductsPage: React.FC = () => {
  const products = [
    {
      id: 'vehicle',
      name: 'Vehicle Insurance',
      icon: CarIcon,
      shortDesc: 'Cars, motorcycles, and more',
      fullDesc: 'Protection for your vehicles with fast claim routing and transparent processing.',
      benefits: ['Quick claim submission', 'Fast processing', 'Document support'],
      cta: 'Learn More',
    },
    {
      id: 'health',
      name: 'Health Insurance',
      icon: HealthIcon,
      shortDesc: 'Medical coverage that cares',
      fullDesc: 'Medical coverage with AI-assisted document processing and fast approvals.',
      benefits: ['Cashless claims', 'Fast approvals', 'AI assistance'],
      cta: 'Learn More',
    },
    {
      id: 'travel',
      name: 'Travel Insurance',
      icon: TravelIcon,
      shortDesc: 'Coverage for your journeys',
      fullDesc: 'Trip protection with 24/7 support for interruptions, delays, and baggage.',
      benefits: ['24/7 support', 'Simple claims', 'Global coverage'],
      cta: 'Learn More',
    },
    {
      id: 'commercial',
      name: 'Commercial Insurance',
      icon: CommercialIcon,
      shortDesc: 'Business protection',
      fullDesc: 'Comprehensive coverage for your business operations and liabilities.',
      benefits: ['Custom coverage', 'Expert support', 'Claims-first'],
      cta: 'Learn More',
    },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-b from-white to-gray-50">
      {/* Hero Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-block mb-4 px-4 py-2 bg-yellow-50 rounded-full border border-yellow-200">
            <p className="text-sm font-medium text-yellow-900">Insurance Made Simple</p>
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold text-gray-900 mt-4">
            Our Products
          </h1>
          <p className="text-xl text-gray-600 mt-6 max-w-2xl mx-auto">
            Insurance built around a claims-first customer experience. Fast, transparent, and designed for you.
          </p>
        </div>
      </section>

      {/* Products Grid */}
      <section className="px-4 py-12 sm:px-6 lg:px-8 pb-24">
        <div className="max-w-7xl mx-auto">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {products.map((product) => {
              const IconComponent = product.icon;
              return (
                <div
                  key={product.id}
                  className="group relative bg-white rounded-2xl border border-gray-200 p-8 hover:shadow-xl hover:border-yellow-300 transition-all duration-300"
                >
                  {/* Accent line */}
                  <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-yellow-400 to-blue-500 rounded-t-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>

                  {/* Icon */}
                  <div className="inline-flex items-center justify-center w-16 h-16 rounded-xl bg-yellow-50 text-yellow-600 mb-6 group-hover:bg-yellow-100 transition-colors duration-300">
                    <IconComponent />
                  </div>

                  {/* Content */}
                  <h3 className="text-lg font-bold text-gray-900 mb-2">
                    {product.name}
                  </h3>
                  <p className="text-sm font-medium text-yellow-600 mb-3">
                    {product.shortDesc}
                  </p>
                  <p className="text-sm text-gray-600 mb-6 leading-relaxed">
                    {product.fullDesc}
                  </p>

                  {/* Benefits */}
                  <ul className="space-y-2 mb-8">
                    {product.benefits.map((benefit) => (
                      <li key={benefit} className="flex items-center gap-2 text-sm text-gray-700">
                        <div className="flex-shrink-0 w-5 h-5 flex items-center justify-center rounded-full bg-emerald-100">
                          <CheckIcon />
                        </div>
                        <span>{benefit}</span>
                      </li>
                    ))}
                  </ul>

                  {/* CTA */}
                  <Link
                    to="/register"
                    className="inline-block w-full text-center px-4 py-3 rounded-lg bg-yellow-400 hover:bg-yellow-500 text-gray-900 font-semibold transition-colors duration-300 group-hover:shadow-lg"
                  >
                    {product.cta}
                  </Link>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="px-4 py-16 sm:px-6 lg:px-8 bg-gradient-to-r from-blue-600 to-blue-800">
        <div className="max-w-2xl mx-auto text-center">
          <h2 className="text-3xl font-bold text-white mb-4">
            Ready to get started?
          </h2>
          <p className="text-blue-100 mb-8">
            Create an account today and manage your insurance with confidence.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Link
              to="/register"
              className="px-8 py-3 bg-yellow-400 hover:bg-yellow-500 text-gray-900 font-semibold rounded-lg transition-colors duration-300"
            >
              Create Account
            </Link>
            <Link
              to="/login"
              className="px-8 py-3 border-2 border-white text-white hover:bg-white hover:text-blue-700 font-semibold rounded-lg transition-colors duration-300"
            >
              Sign In
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
};
