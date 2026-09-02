import React from 'react';

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
  required?: boolean;
}

export const Label: React.FC<LabelProps> = ({
  children,
  required = false,
  className = '',
  ...props
}) => {
  return (
    <label
      className={`block text-sm font-medium text-[var(--color-text-secondary)] mb-1 ${className}`}
      {...props}
    >
      {children}
      {required && <span className="text-[var(--color-danger)] ml-1">*</span>}
    </label>
  );
};
