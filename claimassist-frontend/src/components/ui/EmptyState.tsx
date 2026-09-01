import React from 'react';

export interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  action,
  className = '',
}) => {
  return (
    <div className={`flex flex-col items-center justify-center py-12 px-4 text-center ${className}`}>
      {icon && (
        <div className="mb-4 text-[var(--color-text-muted)]">
          {icon}
        </div>
      )}
      <h3 className="text-lg font-medium text-[var(--color-text-primary)] mb-2">
        {title}
      </h3>
      {description && (
        <p className="text-[var(--color-text-secondary)] mb-6 max-w-md">
          {description}
        </p>
      )}
      {action}
    </div>
  );
};
