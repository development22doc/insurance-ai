import React from 'react';
import { Header } from './Header';
import { Footer } from './Footer';

export interface PublicLayoutProps {
  children: React.ReactNode;
}

export const PublicLayout: React.FC<PublicLayoutProps> = ({ children }) => {
  return (
    <div className="min-h-screen flex flex-col bg-[var(--color-background)]">
      <Header showNavigation={true} />
      <main className="flex-1">{children}</main>
      <Footer />
    </div>
  );
};
