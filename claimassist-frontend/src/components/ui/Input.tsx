import React, { useId } from 'react';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  fullWidth?: boolean;
}

export const Input: React.FC<InputProps> = ({
  label,
  error,
  helperText,
  fullWidth = false,
  className = '',
  id,
  ...props
}) => {
  const generatedId = useId();
  const inputId = id || generatedId;

  const baseStyles = 'px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 transition-colors';
  const normalStyles = 'border-[var(--color-border)] focus:border-[var(--color-primary)] focus:ring-[var(--color-primary)]';
  const errorStyles = 'border-[var(--color-danger)] focus:border-[var(--color-danger)] focus:ring-[var(--color-danger)]';
  const widthStyle = fullWidth ? 'w-full' : '';

  return (
    <div className={fullWidth ? 'w-full' : ''}>
      {label && (
        <label
          htmlFor={inputId}
          className="block text-sm font-medium text-[var(--color-text-secondary)] mb-1"
        >
          {label}
        </label>
      )}
      <input
        id={inputId}
        className={`${baseStyles} ${error ? errorStyles : normalStyles} ${widthStyle} ${className}`}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
        {...props}
      />
      {error && (
        <p id={`${inputId}-error`} className="mt-1 text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}
      {helperText && !error && (
        <p id={`${inputId}-helper`} className="mt-1 text-sm text-[var(--color-text-muted)]">
          {helperText}
        </p>
      )}
    </div>
  );
};
