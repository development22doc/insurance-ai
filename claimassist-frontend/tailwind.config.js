/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // ClaimAssist Brand Colors (Primary: Yellow)
        primary: {
          50: '#FFFBEB',
          100: '#FEF3C7',
          200: '#FDE68A',
          300: '#FCD34D',
          400: '#FBBF24',
          500: '#F59E0B',
          600: '#F9B900', // Brand yellow
          700: '#D97706',
          800: '#B45309',
          900: '#92400E',
        },
        // Secondary Blues
        secondary: {
          50: '#F0F9FF',
          100: '#E0F2FE',
          200: '#BAE6FD',
          300: '#7DD3FC',
          400: '#38BDF8',
          500: '#0EA5E9',
          600: '#0284C7',
          700: '#0369A1', // Secondary blue
          800: '#075985',
          900: '#0C3A66',
        },
        // Neutrals
        gray: {
          50: '#F9FAFB',
          100: '#F3F4F6',
          200: '#E5E7EB',
          300: '#D1D5DB',
          400: '#9CA3AF',
          500: '#6B7280',
          600: '#4B5563',
          700: '#374151',
          800: '#1F2937',
          900: '#111827',
        },
        // Insurance Colors
        success: '#16834A',
        warning: '#D98A00',
        danger: '#D92D20',
      },
      spacing: {
        gutter: '1rem',
        'gutter-lg': '3.5rem',
      },
      fontSize: {
        // Hero typography
        'hero-lg': ['3.5rem', { lineHeight: '1.1', fontWeight: '800' }],
        'hero': ['2.75rem', { lineHeight: '1.1', fontWeight: '800' }],
        // Section headings
        'heading-lg': ['2.5rem', { lineHeight: '1.2', fontWeight: '700' }],
        'heading': ['2rem', { lineHeight: '1.3', fontWeight: '700' }],
        'heading-sm': ['1.5rem', { lineHeight: '1.4', fontWeight: '600' }],
        // Body
        'body-lg': ['1.125rem', { lineHeight: '1.6' }],
        'body': ['1rem', { lineHeight: '1.6' }],
        'body-sm': ['0.875rem', { lineHeight: '1.5' }],
        // Labels
        'label': ['0.875rem', { lineHeight: '1.5', fontWeight: '600' }],
        'caption': ['0.75rem', { lineHeight: '1.5' }],
      },
      borderRadius: {
        'px': '1px',
        'sm': '0.375rem',
        'md': '0.5rem',
        'lg': '0.75rem',
        'xl': '1rem',
        '2xl': '1.5rem',
        '3xl': '2rem',
        'full': '9999px',
      },
      boxShadow: {
        'xs': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
        'sm': '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
        'md': '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
        'lg': '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
        'premium': '0 8px 30px rgba(0, 0, 0, 0.08)',
      },
      maxWidth: {
        'container': '1440px',
        'container-lg': '1360px',
        'container-md': '1024px',
      },
      transitionDuration: {
        'fast': '150ms',
        'normal': '200ms',
        'slow': '300ms',
      },
    },
  },
  plugins: [],
}

