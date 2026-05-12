import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'dashboard', component: LoginComponent }]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const { componentInstance } = TestBed.createComponent(LoginComponent);
    expect(componentInstance).toBeTruthy();
  });

  it('loading is false initially', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    expect(comp.loading()).toBe(false);
  });

  it('error is null initially', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    expect(comp.error()).toBeNull();
  });

  it('currentYear is set', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    expect(comp.currentYear).toBe(new Date().getFullYear());
  });

  it('submit does nothing when form is invalid (empty fields)', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    comp.submit();
    httpTesting.expectNone('/api/v1/auth/login');
    expect(comp.loading()).toBe(false);
  });

  it('loading becomes true while request is in flight and resets to false on success', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    comp.form.setValue({ username: 'admin', password: 'admin' });
    comp.submit();

    expect(comp.loading()).toBe(true);
    httpTesting
      .expectOne('/api/v1/auth/login')
      .flush({ token: 'tok', username: 'admin', role: 'ADMIN', expiresInMs: 28800000 });

    expect(comp.loading()).toBe(false);
  });

  it('shows error and resets loading on 401', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    comp.form.setValue({ username: 'user', password: 'wrong' });
    comp.submit();

    httpTesting.expectOne('/api/v1/auth/login').flush(
      { message: 'Credenciais inválidas' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(comp.error()).toBe('Credenciais inválidas');
    expect(comp.loading()).toBe(false);
  });

  it('shows fallback error when response has no message', () => {
    const { componentInstance: comp } = TestBed.createComponent(LoginComponent);
    comp.form.setValue({ username: 'user', password: 'wrong' });
    comp.submit();

    httpTesting
      .expectOne('/api/v1/auth/login')
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(comp.error()).toBe('Erro ao conectar. Tente novamente.');
  });

  it('redirects to /dashboard immediately when already authenticated', () => {
    // Fake JWT with exp = year 2286 — ensures isAuthenticated() returns true
    const fakeJwt = [
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9',
      'eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxNzE1MDAwMDAwfQ',
      'fake-sig',
    ].join('.');
    localStorage.setItem('msb_token', fakeJwt);

    const router = TestBed.inject(Router);
    const spy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    TestBed.createComponent(LoginComponent);

    expect(spy).toHaveBeenCalledWith(['/dashboard']);

    localStorage.removeItem('msb_token');
    spy.mockRestore();
  });

  it('redirects to /dashboard on successful login', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const router = TestBed.inject(Router);
    const spy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance.form.setValue({ username: 'admin', password: 'admin' });
    fixture.componentInstance.submit();

    httpTesting
      .expectOne('/api/v1/auth/login')
      .flush({ token: 'jwt-tok', username: 'admin', role: 'ADMIN', expiresInMs: 28800000 });

    expect(spy).toHaveBeenCalledWith(['/dashboard']);
    spy.mockRestore();
  });
});
