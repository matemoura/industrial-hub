import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

// JWT payload helper: sub="u", role=ROLE, exp=year 2286
function fakeJwt(role: string, mustChangePassword = false): string {
  const payload = { sub: 'u', role, exp: 9999999999, iat: 1700000000, ...(mustChangePassword ? { mustChangePassword: true } : {}) };
  const b64 = btoa(JSON.stringify(payload)).replace(/=/g, '');
  return `header.${b64}.sig`;
}

describe('adminGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  afterEach(() => localStorage.removeItem('msb_token'));

  function runGuard() {
    return TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
  }

  it('redirects to /login when not authenticated', () => {
    localStorage.removeItem('msb_token');
    const result = runGuard() as UrlTree;
    expect(result.toString()).toBe('/login');
  });

  it('allows ADMIN users', () => {
    localStorage.setItem('msb_token', fakeJwt('ADMIN'));
    expect(runGuard()).toBe(true);
  });

  it('redirects SUPERVISOR to /dashboard', () => {
    localStorage.setItem('msb_token', fakeJwt('SUPERVISOR'));
    const result = runGuard() as UrlTree;
    expect(result.toString()).toBe('/dashboard');
  });

  it('redirects OPERATOR to /dashboard', () => {
    localStorage.setItem('msb_token', fakeJwt('OPERATOR'));
    const result = runGuard() as UrlTree;
    expect(result.toString()).toBe('/dashboard');
  });
});
