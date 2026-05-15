import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { UserListComponent } from './user-list.component';
import { UserResponse } from '../user.service';

const USERS: UserResponse[] = [
  { id: '1', username: 'admin', email: 'admin@msb.com', role: 'ADMIN', active: true, mustChangePassword: false },
  { id: '2', username: 'op', email: 'op@msb.com', role: 'OPERATOR', active: false, mustChangePassword: true },
];

describe('UserListComponent', () => {
  let httpTesting: HttpTestingController;
  let comp: UserListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    comp = fixture.componentInstance;
  });

  beforeEach(() => vi.spyOn(window, 'confirm').mockReturnValue(true));
  afterEach(() => {
    vi.restoreAllMocks();
    httpTesting.verify();
  });

  it('should create', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush([]);
    expect(comp).toBeTruthy();
  });

  it('starts with loading=true and empty users', () => {
    expect(comp.loading()).toBe(true);
    expect(comp.users()).toEqual([]);
    httpTesting.expectOne('/api/v1/admin/users').flush([]);
  });

  it('populates users and sets loading=false on success', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);
    expect(comp.users()).toHaveLength(2);
    expect(comp.loading()).toBe(false);
  });

  it('sets error message and loading=false on API failure', () => {
    httpTesting
      .expectOne('/api/v1/admin/users')
      .flush('', { status: 500, statusText: 'Server Error' });
    expect(comp.error()).toBe('Erro ao carregar usuários.');
    expect(comp.loading()).toBe(false);
  });

  it('openCreateDialog resets form fields and shows dialog', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush([]);

    comp.newUser.username = 'existente';
    comp.openCreateDialog();

    expect(comp.newUser.username).toBe('');
    expect(comp.newUser.email).toBe('');
    expect(comp.newUser.role).toBe('OPERATOR');
    expect(comp.showCreateDialog()).toBe(true);
  });

  it('openRoleDialog sets selected user and shows dialog', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    comp.openRoleDialog(USERS[0]);

    expect(comp.selectedUser()).toEqual(USERS[0]);
    expect(comp.selectedRole()).toBe('ADMIN');
    expect(comp.showRoleDialog()).toBe(true);
  });

  it('submitCreate calls POST and reloads list on success', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush([]);

    comp.newUser.username = 'novouser';
    comp.newUser.email = 'novo@msb.com';
    comp.newUser.temporaryPassword = 'Senha123';
    comp.submitCreate();

    httpTesting.expectOne({ method: 'POST', url: '/api/v1/admin/users' }).flush(USERS[0]);
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    expect(comp.showCreateDialog()).toBe(false);
    expect(comp.successMessage()).toBe('Usuário criado com sucesso.');
  });

  it('submitCreate shows error message on 409', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush([]);

    comp.submitCreate();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/admin/users' }).flush(
      { message: 'Username já existe: novouser' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(comp.error()).toBe('Username já existe: novouser');
  });

  it('toggleActive does nothing when user cancels confirmation', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    comp.toggleActive(USERS[0]);
    httpTesting.expectNone('/api/v1/admin/users/1/deactivate');
  });

  it('toggleActive deactivates an active user', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    comp.toggleActive(USERS[0]);
    httpTesting
      .expectOne({ method: 'PUT', url: '/api/v1/admin/users/1/deactivate' })
      .flush(null, { status: 204, statusText: 'No Content' });
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    expect(comp.successMessage()).toBe('Usuário desativado.');
  });

  it('toggleActive reactivates an inactive user', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    comp.toggleActive(USERS[1]);
    httpTesting
      .expectOne({ method: 'PUT', url: '/api/v1/admin/users/2/reactivate' })
      .flush(null, { status: 204, statusText: 'No Content' });
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    expect(comp.successMessage()).toBe('Usuário reativado.');
  });

  it('toggleActive shows API error message on 422 (last admin)', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush(USERS);

    comp.toggleActive(USERS[0]);
    httpTesting
      .expectOne({ method: 'PUT', url: '/api/v1/admin/users/1/deactivate' })
      .flush(
        { message: 'Não é possível desativar o único administrador ativo' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    expect(comp.error()).toBe('Não é possível desativar o único administrador ativo');
  });

  it('dismissError clears the error signal', () => {
    httpTesting
      .expectOne('/api/v1/admin/users')
      .flush('', { status: 500, statusText: 'Server Error' });

    expect(comp.error()).not.toBeNull();
    comp.dismissError();
    expect(comp.error()).toBeNull();
  });

  it('submitRoleChange does nothing if no user is selected', () => {
    httpTesting.expectOne('/api/v1/admin/users').flush([]);

    comp.submitRoleChange();
    httpTesting.expectNone('/api/v1/admin/users/undefined/role');
  });
});
