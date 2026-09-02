import React, { useId } from 'react';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helperText?: string;
  fullWidth?: boolean;
}

export const Textarea: React.FC<TextareaProps> = ({
  label,
  error,
  helperText,
  fullWidth = false,
  className = '',
  id,
  ...props
}) => {
  const generatedId = useId();
  const textareaId = id || generatedId;

  const baseStyles = 'px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 transition-colors resize-y';
  const normalStyles = 'border-[var(--color-border)] focus:border-[var(--color-primary)] focus:ring-[var(--color-primary)]';
  const errorStyles = 'border-[var(--color-danger)] focus:border-[var(--color-danger)] focus:ring-[var(--color-danger)]';
  const widthStyle = fullWidth ? 'w-full' : '';

  return (
    <div className={fullWidth ? 'w-full' : ''}>
      {label && (
        <label
          htmlFor={textareaId}
          className="block text-sm font-medium text-[var(--color-text-secondary)] mb-1"
        >
          {label}
        </label>
      )}
      <textarea
        id={textareaId}
        className={`${baseStyles} ${error ? errorStyles : normalStyles} ${widthStyle} ${className}`}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${textareaId}-error` : helperText ? `${textareaId}-helper` : undefined}
        {...props}
      />
      {error && (
        <p id={`${textareaId}-error`} className="mt-1 text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}
      {helperText && !error && (
        <p id={`${textareaId}-helper`} className="mt-1 text-sm text-[var(--color-text-muted)]">
          {helperText}
        </p>
      )}
    </div>
  );
};
