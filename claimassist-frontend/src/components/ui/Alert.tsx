import React from 'react';

export interface AlertProps {
  children: React.ReactNode;
  variant?: 'info' | 'success' | 'warning' | 'danger';
  className?: string;
  onClose?: () => void;
}

export const Alert: React.FC<AlertProps> = ({
  children,
  variant = 'info',
  className = '',
  onClose,
}) => {
  const variantStyles = {
    info: 'bg-[var(--color-info-light)] border-[var(--color-info)] text-[var(--color-info)]',
    success: 'bg-[var(--color-success-light)] border-[var(--color-success)] text-[var(--color-success)]',
    warning: 'bg-[var(--color-warning-light)] border-[var(--color-warning)] text-[var(--color-warning)]',
    danger: 'bg-[var(--color-danger-light)] border-[var(--color-danger)] text-[var(--color-danger)]',
  };

  return (
    <div
      className={`border rounded-lg p-4 ${variantStyles[variant]} ${className}`}
      role="alert"
    >
      <div className="flex items-start">
        <div className="flex-1">{children}</div>
        {onClose && (
          <button
            onClick={onClose}
            className="ml-4 inline-flex text-current hover:opacity-75 focus:outline-none"
            aria-label="Close"
          >
            <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
};
