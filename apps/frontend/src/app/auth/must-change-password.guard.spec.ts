import { TestBed } from '@angular/core/testing';
import { provideRouter, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { mustChangePasswordGuard } from './must-change-password.guard';

function fakeJwt(mustChangePassword: boolean, expired = false): string {
  const exp = expired ? 1000000 : 9999999999;
  const payload = { sub: 'u', role: 'OPERATOR', exp, iat: 1700000000, ...(mustChangePassword ? { mustChangePassword: true } : {}) };
  const b64 = btoa(JSON.stringify(payload)).replace(/=/g, '');
  return `header.${b64}.sig`;
}

describe('mustChangePasswordGuard', () => {
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
    return TestBed.runInInjectionContext(() => mustChangePasswordGuard({} as never, {} as never));
  }

  it('allows navigation when not authenticated (authGuard handles)', () => {
    localStorage.removeItem('msb_token');
    expect(runGuard()).toBe(true);
  });

  it('allows navigation when authenticated and mustChangePassword=false', () => {
    localStorage.setItem('msb_token', fakeJwt(false));
    expect(runGuard()).toBe(true);
  });

  it('redirects to /change-password when mustChangePassword=true', () => {
    localStorage.setItem('msb_token', fakeJwt(true));
    const result = runGuard() as UrlTree;
    expect(result.toString()).toBe('/change-password');
  });

  it('allows navigation when token is expired (isAuthenticated=false)', () => {
    localStorage.setItem('msb_token', fakeJwt(true, /* expired= */ true));
    expect(runGuard()).toBe(true);
  });
});
