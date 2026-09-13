import {render} from "@testing-library/react";

parse code and state from URL search params', () => {
      const params = new URLSearchParams('?code=test-code&state=test-state');
      expect(params.get('code')).toBe('test-code');
      expect(params.get('state')).toBe('test-state');
    });

    it('should show error state for missing code or state', () => {
      Object.defineProperty(window, 'location', {
        value: {
          ...window.location,
          search: '',
        },
        writable: true,
        configurable: true,
      });

      render(
        <AuthProvider>
          <AuthCallbackPage />
        </AuthProvider>
      );

      expect(screen.getByText('Sign-In Session Expired')).toBeDefined();
      expect(screen.getByText('Your sign-in session has expired. Please sign in again.    it('should navigate to Gateway callback when code and state present', async () => {
      const assign = vi.fn();
      Object.defineProperty(window, 'location', {
        value: {
          href: 'http://localhost:5173/callback?code=test-code&state=test-state',
          pathname: '/callback',
          search: '?code=test-code&state=test-state',
          hash: '',
          origin: 'http://localhost:5173',
          assign,
        },
        writable: true,
        configurable: true,
      });

      render(
        <AuthProvider>
          <AuthCallbackPage />
        </AuthProvider>
      );

      await waitFor(() => {
        expect(assign).toHaveBeenCalled();
        const calledWith = (assign as any).mock.calls[0][0];
        expect(typeof calledWith).toBe('string');
        expect(calledWith.startsWith('http://localhost:8080/customer/auth/callback?code=test-code')).toBe(true);
      });
    it('should show error UI when code/state are missing', () => {
          pathname: '/callback',
          href: 'http://localhost:5173/callback',
      expect(screen.getByText('Authentication Error')).toBeDefined();
