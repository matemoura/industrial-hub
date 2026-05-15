import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserResponse, UserService, CreateUserRequest } from '../user.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LowerCasePipe, FormsModule],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);

  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly showCreateDialog = signal(false);
  readonly showRoleDialog = signal(false);
  readonly selectedUser = signal<UserResponse | null>(null);

  readonly newUser: CreateUserRequest = {
    username: '',
    email: '',
    role: 'OPERATOR',
    temporaryPassword: '',
  };

  readonly selectedRole = signal<'OPERATOR' | 'SUPERVISOR' | 'ADMIN'>('OPERATOR');

  readonly roles: Array<'OPERATOR' | 'SUPERVISOR' | 'ADMIN'> = ['OPERATOR', 'SUPERVISOR', 'ADMIN'];

  ngOnInit(): void {
    this.loadUsers();
  }

  private loadUsers(): void {
    this.loading.set(true);
    this.userService.list().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Erro ao carregar usuários.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    this.newUser.username = '';
    this.newUser.email = '';
    this.newUser.role = 'OPERATOR';
    this.newUser.temporaryPassword = '';
    this.showCreateDialog.set(true);
  }

  submitCreate(): void {
    this.userService.create({ ...this.newUser }).subscribe({
      next: () => {
        this.showCreateDialog.set(false);
        this.showSuccess('Usuário criado com sucesso.');
        this.loadUsers();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Erro ao criar usuário.'),
    });
  }

  openRoleDialog(user: UserResponse): void {
    this.selectedUser.set(user);
    this.selectedRole.set(user.role);
    this.showRoleDialog.set(true);
  }

  submitRoleChange(): void {
    const user = this.selectedUser();
    if (!user) return;
    this.userService.updateRole(user.id, { role: this.selectedRole() }).subscribe({
      next: () => {
        this.showRoleDialog.set(false);
        this.showSuccess('Role atualizado com sucesso.');
        this.loadUsers();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Erro ao atualizar role.'),
    });
  }

  toggleActive(user: UserResponse): void {
    const label = user.active ? 'desativar' : 'reativar';
    if (!confirm(`Confirmar ${label} o usuário "${user.username}"?`)) return;

    const action = user.active
      ? this.userService.deactivate(user.id)
      : this.userService.reactivate(user.id);

    action.subscribe({
      next: () => {
        this.showSuccess(user.active ? 'Usuário desativado.' : 'Usuário reativado.');
        this.loadUsers();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Erro ao alterar status.'),
    });
  }

  private showSuccess(message: string): void {
    this.successMessage.set(message);
    this.error.set(null);
    setTimeout(() => this.successMessage.set(null), 3000);
  }

  dismissError(): void {
    this.error.set(null);
  }
}
