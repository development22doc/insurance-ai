import { type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes, type ReactNode } from 'react';

const baseField = 'w-full rounded-xl border bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 disabled:bg-slate-50 disabled:text-slate-500';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: ReactNode;
}

export function Field({ label, error, hint, required, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label className="block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {error && <p className="text-xs text-red-600 font-medium">{error}</p>}
    </div>
  );
}

const errorBorder = (error?: string) => (error ? 'border-red-300' : 'border-slate-200');

export function Input({ error, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { error?: string }) {
  return <input className={`${baseField} ${errorBorder(error)} ${className}`} {...props} />;
}

export function Select({ error, className = '', children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { error?: string }) {
  return (
    <select className={`${baseField} ${errorBorder(error)} appearance-none bg-no-repeat ${className}`}
      style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2364748b' d='M6 8L2 4h8z'/%3E%3C/svg%3E\")", backgroundPosition: 'right 1rem center', backgroundSize: '12px', paddingRight: '2.5rem' }}
      {...props}
    >
      {children}
    </select>
  );
}

export function Textarea({ error, className = '', ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { error?: string }) {
  return <textarea className={`${baseField} ${errorBorder(error)} resize-none ${className}`} {...props} />;
}
