import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { ChangePasswordComponent } from './change-password.component';
import { AuthService } from '../auth.service';

const CHANGE_PASSWORD_URL = '/api/v1/users/me/password';

describe('ChangePasswordComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'dashboard', component: ChangePasswordComponent }]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.removeItem('msb_token');
  });

  it('should create', () => {
    const { componentInstance } = TestBed.createComponent(ChangePasswordComponent);
    expect(componentInstance).toBeTruthy();
  });

  it('form is invalid when all fields are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    expect(comp.form.invalid).toBe(true);
  });

  it('form is invalid when newPassword shorter than 8 chars', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'Ab1', confirmPassword: 'Ab1' });
    expect(comp.form.invalid).toBe(true);
  });

  it('passwordsMismatch is false when passwords match', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'NovaSenha1' });
    comp.form.get('confirmPassword')!.markAsTouched();
    expect(comp.passwordsMismatch).toBe(false);
  });

  it('passwordsMismatch is true when passwords differ and confirmPassword is touched', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'Diferente1' });
    comp.form.get('confirmPassword')!.markAsTouched();
    expect(comp.passwordsMismatch).toBe(true);
  });

  it('submit does nothing when form is invalid', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.submit();
    httpTesting.expectNone(CHANGE_PASSWORD_URL);
    expect(comp.loading()).toBe(false);
  });

  it('loading is true while request is in flight', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'NovaSenha1' });
    comp.submit();
    expect(comp.loading()).toBe(true);
    httpTesting.expectOne(CHANGE_PASSWORD_URL).flush(
      { token: 'new-jwt', username: 'joao', role: 'OPERATOR', expiresInMs: 28800000, mustChangePassword: false },
    );
  });

  it('on success: updates token in AuthService and navigates to dashboard', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    const auth = TestBed.inject(AuthService);
    const router = TestBed.inject(Router);
    const updateTokenSpy = vi.spyOn(auth, 'updateToken');
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'NovaSenha1' });
    comp.submit();

    httpTesting.expectOne(CHANGE_PASSWORD_URL).flush({
      token: 'new-jwt', username: 'joao', role: 'OPERATOR', expiresInMs: 28800000, mustChangePassword: false,
    });

    expect(updateTokenSpy).toHaveBeenCalledWith('new-jwt');
    expect(navSpy).toHaveBeenCalledWith(['/dashboard']);
    expect(comp.loading()).toBe(false);

    updateTokenSpy.mockRestore();
    navSpy.mockRestore();
  });

  it('shows API error message on failure and resets loading', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'NovaSenha1' });
    comp.submit();

    httpTesting.expectOne(CHANGE_PASSWORD_URL).flush(
      { message: 'Senha atual incorreta' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(comp.error()).toBe('Senha atual incorreta');
    expect(comp.loading()).toBe(false);
  });

  it('shows fallback error when response has no message', () => {
    const { componentInstance: comp } = TestBed.createComponent(ChangePasswordComponent);
    comp.form.setValue({ currentPassword: 'Atual123', newPassword: 'NovaSenha1', confirmPassword: 'NovaSenha1' });
    comp.submit();

    httpTesting.expectOne(CHANGE_PASSWORD_URL).flush(
      {},
      { status: 500, statusText: 'Server Error' },
    );

    expect(comp.error()).toBe('Erro ao alterar senha. Tente novamente.');
  });
});
