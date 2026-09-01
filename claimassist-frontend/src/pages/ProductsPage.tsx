import React from 'react';
import { Button, Card } from '../components/ui';
import { Link } from 'react-router-dom';

export const ProductsPage: React.FC = () => {
  const products = [
    {
      id: 'vehicle',
      name: 'Vehicle Insurance',
      desc: 'Protection for cars, motorcycles, and commercial vehicles with fast claim routing.',
      benefits: ['Roadside support', 'Quick repairs', 'Transparent claims'],
    },
    {
      id: 'health',
      name: 'Health Insurance',
      desc: 'Medical coverage with document-driven claims and AI-assisted extraction.',
      benefits: ['Cashless claims', 'Fast approvals', 'Document upload'],
    },
    {
      id: 'travel',
      name: 'Travel Insurance',
      desc: 'Coverage for trip interruptions, delays, and baggage loss.',
      benefits: ['24/7 support', 'Simple claims', 'Global coverage'],
    },
  ];

  return (
    <div className="py-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="text-3xl font-bold">Products</h1>
        <p className="mt-2 text-[var(--color-text-secondary)] max-w-2xl">Insurance products built around a claims-first customer experience.</p>

        <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {products.map(p => (
            <Card key={p.id} padding="md">
              <h3 className="text-xl font-semibold">{p.name}</h3>
              <p className="text-[var(--color-text-secondary)] mt-2">{p.desc}</p>
              <ul className="mt-4 list-disc list-inside text-[var(--color-text-secondary)]">
                {p.benefits.map(b => <li key={b}>{b}</li>)}
              </ul>

              <div className="mt-4">
                <Link to="/register">
                  <Button size="sm">Get started</Button>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
};
