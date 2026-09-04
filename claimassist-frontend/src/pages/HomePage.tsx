import React from 'react';
import { Link } from 'react-router-dom';
import { CarIcon, BikeIcon, HealthIcon, HomeIcon, TravelIcon, CommercialIcon, ClaimsIcon, CheckIcon } from '../components/icons/InsuranceIcons';

export const HomePage: React.FC = () => {
  return (
    <div>
      {/* Hero Section */}
      <section className="hero">
        <div className="container">
          <div className="hero-content">
            <div className="hero-left">
              <div className="hero-badge">Digital claims, made simple</div>

              <h1 className="hero-headline">
                Manage your insurance, your way
              </h1>

              <p className="hero-subheadline">
                Submit claims, track progress, and manage policies—all in one secure place.
                Our AI assistant helps every step of the way.
              </p>

              <div className="hero-ctas">
                <Link to="/policies" className="btn-primary">
                  View My Policies
                </Link>
                <Link to="/claims/new" className="btn-secondary">
                  File a Claim
                </Link>
              </div>
            </div>

            <div className="hero-right">
              <div className="hero-illustration">
                <svg width="280" height="260" viewBox="0 0 300 280" fill="none" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid meet">
                  {/* Shield with checkmark - professional insurance icon */}
                  <defs>
                    <linearGradient id="shieldGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" style={{ stopColor: '#F9B900', stopOpacity: 0.15 }} />
                      <stop offset="100%" style={{ stopColor: '#0369A1', stopOpacity: 0.15 }} />
                    </linearGradient>
                  </defs>
                  <path d="M150 20L80 50V120C80 180 150 240 150 240C150 240 220 180 220 120V50L150 20Z" fill="url(#shieldGradient)" stroke="#0369A1" strokeWidth="2"/>
                  <path d="M130 140L145 155L175 120" stroke="#F9B900" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                  <circle cx="150" cy="80" r="30" fill="none" stroke="#F9B900" strokeWidth="2" opacity="0.3"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Insurance Categories Section */}
      <section className="section" style={{ backgroundColor: 'var(--surface-white)', borderBottom: '1px solid var(--border)' }}>
        <div className="container">
          <h2 className="section-title">Insurance Products</h2>
          <p className="section-subtitle">
            View policies, file claims, and get support for your coverage.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: '1.5rem', marginTop: '2rem' }}>
            {/* Category Icons */}
            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <CarIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Car</p>
            </div>

            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <BikeIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Bike</p>
            </div>

            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <HealthIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Health</p>
            </div>

            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <HomeIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Home</p>
            </div>

            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <TravelIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Travel</p>
            </div>

            <div style={{ textAlign: 'center', cursor: 'pointer', transition: 'all 200ms' }} className="category-hover">
              <div style={{
                width: '96px',
                height: '96px',
                backgroundColor: 'var(--surface-white)',
                border: '2px solid var(--border)',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: '#0369A1',
                transition: 'all 200ms',
                fontSize: '32px'
              }}>
                <CommercialIcon />
              </div>
              <p style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>Commercial</p>
            </div>
          </div>
        </div>
      </section>

      {/* Claims-First Action Section */}
      <section className="section" style={{ backgroundColor: 'var(--surface)' }}>
        <div className="container">
          <h2 className="section-title">Manage Your Claims with Confidence</h2>
          <p className="section-subtitle" style={{ maxWidth: '700px' }}>
            From filing to settlement, ClaimAssist keeps you in control. Track every step, upload documents,
            and get AI-powered assistance—all in one place.
          </p>

          <div className="action-card" style={{ marginTop: '2rem' }}>
            <div className="action-grid">
              <div className="action-item">
                <div style={{ fontSize: '32px', marginBottom: '1rem', color: 'var(--primary)' }}>
                  <ClaimsIcon />
                </div>
                <h4 className="action-heading">File a Claim</h4>
                <p className="action-description">Submit a new claim with document upload and AI-assisted form assistance.</p>
                <Link to="/claims/new" className="action-button" style={{ marginTop: '1rem' }}>
                  Start Claim →
                </Link>
              </div>

              <div className="action-item">
                <div style={{ fontSize: '32px', marginBottom: '1rem', color: 'var(--primary)' }}>
                  <ClaimsIcon />
                </div>
                <h4 className="action-heading">Track Claims</h4>
                <p className="action-description">View real-time status updates and communicate with adjusters.</p>
                <Link to="/claims" className="action-button" style={{ marginTop: '1rem' }}>
                  View Claims →
                </Link>
              </div>

              <div className="action-item">
                <div style={{ fontSize: '32px', marginBottom: '1rem', color: 'var(--primary)' }}>
                  <CheckIcon />
                </div>
                <h4 className="action-heading">Get AI Assistance</h4>
                <p className="action-description">Get help with claim assessment and document analysis.</p>
                <Link to="/dashboard" className="action-button" style={{ marginTop: '1rem' }}>
                  Learn More →
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How It Works - Claims Experience */}
      <section className="section" style={{ backgroundColor: 'var(--surface-white)' }}>
        <div className="container">
          <h2 className="section-title">The ClaimAssist Experience</h2>

          <div className="grid grid-3" style={{ marginTop: '2rem' }}>
            <div className="card">
              <div style={{ fontSize: '36px', marginBottom: '1rem', color: 'var(--primary)' }}>1</div>
              <h4 className="card-title">Submit & Track</h4>
              <p className="card-description">
                File your claim in minutes with document upload. Get instant confirmation and tracking link.
              </p>
            </div>

            <div className="card">
              <div style={{ fontSize: '36px', marginBottom: '1rem', color: 'var(--primary)' }}>2</div>
              <h4 className="card-title">AI Assistance</h4>
              <p className="card-description">
                Our AI assistant helps validate documents, extracts key information, and answers common questions.
              </p>
            </div>

            <div className="card">
              <div style={{ fontSize: '36px', marginBottom: '1rem', color: 'var(--primary)' }}>3</div>
              <h4 className="card-title">Updates & Resolution</h4>
              <p className="card-description">
                Receive status updates in real-time. View assessor notes and get claim decisions quickly.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Trust & Capabilities Section */}
      <section className="section" style={{ backgroundColor: 'var(--surface)' }}>
        <div className="container">
          <h2 className="section-title">Why Choose ClaimAssist?</h2>

          <div className="grid grid-2">
            <div style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1rem' }}>
                <div style={{ color: 'var(--success)', fontSize: '20px', flexShrink: 0 }}>✓</div>
                <div>
                  <h4 style={{ fontSize: '1.125rem', fontWeight: '700', marginBottom: '0.5rem', color: 'var(--text-primary)' }}>
                    Bank-Level Security
                  </h4>
                  <p style={{ color: 'var(--text-secondary)', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    End-to-end encryption and secure authentication protect your data and documents.
                  </p>
                </div>
              </div>
            </div>

            <div style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1rem' }}>
                <div style={{ color: 'var(--success)', fontSize: '20px', flexShrink: 0 }}>✓</div>
                <div>
                  <h4 style={{ fontSize: '1.125rem', fontWeight: '700', marginBottom: '0.5rem', color: 'var(--text-primary)' }}>
                    Real-Time Tracking
                  </h4>
                  <p style={{ color: 'var(--text-secondary)', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    View claim status at every step. No surprises, complete transparency.
                  </p>
                </div>
              </div>
            </div>

            <div style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1rem' }}>
                <div style={{ color: 'var(--success)', fontSize: '20px', flexShrink: 0 }}>✓</div>
                <div>
                  <h4 style={{ fontSize: '1.125rem', fontWeight: '700', marginBottom: '0.5rem', color: 'var(--text-primary)' }}>
                    AI-Powered Support
                  </h4>
                  <p style={{ color: 'var(--text-secondary)', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    Intelligent document analysis and form assistance for faster processing.
                  </p>
                </div>
              </div>
            </div>

            <div style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1rem' }}>
                <div style={{ color: 'var(--success)', fontSize: '20px', flexShrink: 0 }}>✓</div>
                <div>
                  <h4 style={{ fontSize: '1.125rem', fontWeight: '700', marginBottom: '0.5rem', color: 'var(--text-primary)' }}>
                    Multi-Role Access
                  </h4>
                  <p style={{ color: 'var(--text-secondary)', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    Seamless experience for customers, adjusters, auditors, and administrators.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Final CTA Section */}
      <section className="section" style={{ backgroundColor: 'var(--surface-white)', textAlign: 'center' }}>
        <div className="container">
          <h2 className="section-title">Ready to Simplify Your Claims?</h2>
          <p className="section-subtitle" style={{ margin: '0 auto 2rem', maxWidth: '600px' }}>
            Join customers who trust ClaimAssist to manage their insurance claims with confidence.
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to="/dashboard" className="btn-primary">View My Policies</Link>
            <Link to="/claims" className="btn-secondary">File a Claim</Link>
          </div>
        </div>
      </section>
    </div>
  );
};
