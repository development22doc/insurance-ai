import React from 'react';
import { Link } from 'react-router-dom';
import type { ClaimResponse } from '../../types';

interface ClaimSubmissionSuccessProps {
  claim: ClaimResponse;
  onFileAnother: () => void;
}

export const ClaimSubmissionSuccess: React.FC<ClaimSubmissionSuccessProps> = ({ claim, onFileAnother }) => {
  return (
    <div className="space-y-6">
      {/* Success Header */}
      <div className="text-center">
        <div className="inline-block mb-4 p-3 bg-green-50 rounded-full">
          <svg className="w-8 h-8 text-green-600" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold mb-2">Claim Submitted Successfully</h1>
        <p className="text-[var(--color-text-secondary)]">Your claim has been received and is now being processed.</p>
      </div>

      {/* Claim Details Card */}
      <div className="bg-[var(--color-background)] border border-[var(--color-border)] rounded-lg p-6 space-y-4 max-w-2xl mx-auto w-full">
        <div>
          <h2 className="text-lg font-semibold mb-4">Claim Details</h2>
          <dl className="space-y-3">
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Claim Number</dt>
              <dd className="text-lg font-semibold mt-1">{claim.claimNumber}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Claim ID</dt>
              <dd className="text-sm text-[var(--color-text-secondary)] font-mono mt-1">{claim.id}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Incident Type</dt>
              <dd className="text-sm mt-1">{claim.incidentType}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-[var(--color-text-secondary)]">Current Status</dt>
              <dd className="text-sm mt-1">
                <span className="inline-block px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs font-medium">
                  {claim.status}
                </span>
              </dd>
            </div>
          </dl>
        </div>
      </div>

      {/* Actions */}
      <div className="flex flex-col sm:flex-row gap-3 justify-center max-w-2xl mx-auto w-full">
        <Link
          to={`/claims/${claim.id}`}
          className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg text-center hover:opacity-90 transition-opacity"
        >
          Track Claim
        </Link>
        <Link
          to="/claims"
          className="px-4 py-2 border border-[var(--color-border)] rounded-lg text-center hover:bg-[var(--color-background)] transition-colors"
        >
          View All Claims
        </Link>
        <button
          onClick={onFileAnother}
          className="px-4 py-2 border border-[var(--color-border)] rounded-lg hover:bg-[var(--color-background)] transition-colors"
        >
          File Another Claim
        </button>
      </div>
    </div>
  );
};

