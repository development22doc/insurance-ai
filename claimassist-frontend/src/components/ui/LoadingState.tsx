import React from 'react';
import { Spinner } from './Spinner';

export interface LoadingStateProps {
  message?: string;
  className?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({
  message = 'Loading...',
  className = '',
}) => {
  return (
    <div className={`flex flex-col items-center justify-center py-12 ${className}`}>
      <Spinner size="lg" className="mb-4" />
      <p className="text-[var(--color-text-secondary)]">{message}</p>
    </div>
  );
};
