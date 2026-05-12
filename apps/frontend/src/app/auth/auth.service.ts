import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
  expiresInMs: number;
}

interface JwtPayload {
  sub: string;
  role: string;
  exp: number;
  iat: number;
}

const TOKEN_KEY = 'msb_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly _payload = computed(() => this.decodePayload(this._token()));

  readonly isAuthenticated = computed(() => {
    const p = this._payload();
    return p ? Date.now() < p.exp * 1000 : false;
  });

  readonly role = computed(() => this._payload()?.role ?? null);
  readonly username = computed(() => this._payload()?.sub ?? null);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', credentials).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.token);
        this._token.set(res.token);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this._token.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return this._token();
  }

  private decodePayload(token: string | null): JwtPayload | null {
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(b64)) as JwtPayload;
    } catch {
      return null;
    }
  }
}
