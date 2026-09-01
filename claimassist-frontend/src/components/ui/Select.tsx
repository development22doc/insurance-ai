import React, { useId } from 'react';

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  helperText?: string;
  fullWidth?: boolean;
  options: { value: string; label: string }[];
}

export const Select: React.FC<SelectProps> = ({
  label,
  error,
  helperText,
  fullWidth = false,
  options,
  className = '',
  id,
  ...props
}) => {
  const generatedId = useId();
  const selectId = id || generatedId;

  const baseStyles = 'px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 transition-colors bg-[var(--color-background)]';
  const normalStyles = 'border-[var(--color-border)] focus:border-[var(--color-primary)] focus:ring-[var(--color-primary)]';
  const errorStyles = 'border-[var(--color-danger)] focus:border-[var(--color-danger)] focus:ring-[var(--color-danger)]';
  const widthStyle = fullWidth ? 'w-full' : '';

  return (
    <div className={fullWidth ? 'w-full' : ''}>
      {label && (
        <label
          htmlFor={selectId}
          className="block text-sm font-medium text-[var(--color-text-secondary)] mb-1"
        >
          {label}
        </label>
      )}
      <select
        id={selectId}
        className={`${baseStyles} ${error ? errorStyles : normalStyles} ${widthStyle} ${className}`}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${selectId}-error` : helperText ? `${selectId}-helper` : undefined}
        {...props}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <p id={`${selectId}-error`} className="mt-1 text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}
      {helperText && !error && (
        <p id={`${selectId}-helper`} className="mt-1 text-sm text-[var(--color-text-muted)]">
          {helperText}
        </p>
      )}
    </div>
  );
};
