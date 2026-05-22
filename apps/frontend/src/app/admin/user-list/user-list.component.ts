import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserResponse, UserService, CreateUserRequest } from '../user.service';
import { PlantResponse, PlantService } from '../plants/plant.service';
import { AuthService } from '../../auth/auth.service';
import { AdminService } from '../admin.service';

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
  private readonly plantService = inject(PlantService);
  private readonly authService = inject(AuthService);
  private readonly adminService = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentUsername = computed(() => this.authService.username() ?? '');

  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly showCreateDialog = signal(false);
  readonly showRoleDialog = signal(false);
  readonly showPlantsDialog = signal(false);
  readonly showAnonDialog = signal(false);
  readonly selectedUser = signal<UserResponse | null>(null);

  // Anonymize dialog (AC#14 — US-068)
  readonly anonTarget = signal<UserResponse | null>(null);
  readonly anonConfirmText = signal('');
  readonly anonLoading = signal(false);
  readonly anonError = signal<string | null>(null);
  readonly anonConfirmValid = computed(
    () => this.anonConfirmText() === this.anonTarget()?.username,
  );

  // Plants section (AC#18)
  readonly canManagePlants = computed(() => this.authService.role() === 'ADMIN');
  readonly userPlants = signal<PlantResponse[]>([]);
  readonly availablePlants = signal<PlantResponse[]>([]);
  readonly plantsLoading = signal(false);

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

  openPlantsDialog(user: UserResponse): void {
    this.selectedUser.set(user);
    this.userPlants.set([]);
    this.availablePlants.set([]);
    this.showPlantsDialog.set(true);
    this.plantsLoading.set(true);

    // Carrega plantas do usuário e todas as plantas em paralelo
    this.plantService.getUserPlants(user.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (plants) => {
          this.userPlants.set(plants);
          this.loadAvailablePlants(plants.map((p) => p.id));
        },
        error: (err) => {
          this.error.set(err?.error?.message ?? 'Erro ao carregar plantas do usuário.');
          this.plantsLoading.set(false);
        },
      });
  }

  private loadAvailablePlants(assignedIds: string[]): void {
    this.plantService.listPlants()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (allPlants) => {
          // Filtra plantas já vinculadas, exibindo somente as disponíveis para adicionar
          const available = allPlants.filter((p) => p.active && !assignedIds.includes(p.id));
          this.availablePlants.set(available);
          this.plantsLoading.set(false);
        },
        error: () => {
          this.plantsLoading.set(false);
        },
      });
  }

  assignPlant(plantId: string): void {
    const user = this.selectedUser();
    if (!user) return;
    const currentIds = this.userPlants().map((p) => p.id);
    if (currentIds.includes(plantId)) return;

    this.plantService.assignUserPlants(user.id, [...currentIds, plantId])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.showSuccess('Planta vinculada com sucesso.');
          // Recarrega a lista de plantas do usuário no dialog
          this.plantService.getUserPlants(user.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (plants) => {
                this.userPlants.set(plants);
                this.loadAvailablePlants(plants.map((p) => p.id));
              },
            });
        },
        error: (err) => this.error.set(err?.error?.message ?? 'Erro ao vincular planta.'),
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

  // ─── Anonymize (AC#14 — US-068) ──────────────────────────────────────────

  isOwnAccount(user: UserResponse): boolean {
    return user.username === this.currentUsername();
  }

  isAlreadyAnonymized(user: UserResponse): boolean {
    return user.username.startsWith('[usuario-');
  }

  openAnonDialog(user: UserResponse): void {
    this.anonTarget.set(user);
    this.anonConfirmText.set('');
    this.anonError.set(null);
    this.showAnonDialog.set(true);
  }

  closeAnonDialog(): void {
    this.showAnonDialog.set(false);
    this.anonTarget.set(null);
    this.anonConfirmText.set('');
    this.anonError.set(null);
  }

  submitAnonymize(): void {
    const user = this.anonTarget();
    if (!user || !this.anonConfirmValid()) return;

    this.anonLoading.set(true);
    this.anonError.set(null);
    this.adminService.anonymizeUser(user.id).subscribe({
      next: () => {
        this.anonLoading.set(false);
        this.closeAnonDialog();
        this.showSuccess('Usuário anonimizado com sucesso.');
        this.loadUsers();
      },
      error: (err: { error?: { message?: string } }) => {
        this.anonLoading.set(false);
        this.anonError.set(err?.error?.message ?? 'Erro ao anonimizar usuário.');
      },
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
