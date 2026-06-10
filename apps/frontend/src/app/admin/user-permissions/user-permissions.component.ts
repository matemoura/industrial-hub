import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AppModule,
  MODULE_LABELS,
  UserModulePermissionResponse,
  UserService,
} from '../user.service';

@Component({
  selector: 'app-user-permissions',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './user-permissions.component.html',
  styleUrl: './user-permissions.component.scss',
})
export class UserPermissionsComponent implements OnInit {
  private readonly userService = inject(UserService);

  readonly userId = input.required<string>();

  readonly permissions = signal<UserModulePermissionResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly moduleLabels = MODULE_LABELS;
  readonly allModules: AppModule[] = [
    'OEE',
    'QMS',
    'MAINTENANCE',
    'PRODUCTION',
    'TRAINING',
    'CHANGES',
    'MANAGEMENT_REVIEW',
  ];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.errorMsg.set(null);
    this.userService.getUserPermissions(this.userId()).subscribe({
      next: (perms) => {
        // Garantir que todos os módulos estejam presentes
        const map = new Map(perms.map((p) => [p.module, p]));
        const full: UserModulePermissionResponse[] = this.allModules.map((m) =>
          map.get(m) ?? { module: m, canView: false, canCreate: false, canEdit: false, canDelete: false },
        );
        this.permissions.set(full);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar permissões.');
        this.isLoading.set(false);
      },
    });
  }

  togglePerm(
    module: AppModule,
    field: 'canView' | 'canCreate' | 'canEdit' | 'canDelete',
  ): void {
    this.permissions.update((list) =>
      list.map((p) =>
        p.module === module ? { ...p, [field]: !p[field] } : p,
      ),
    );
  }

  getPerm(module: AppModule): UserModulePermissionResponse {
    return (
      this.permissions().find((p) => p.module === module) ?? {
        module,
        canView: false,
        canCreate: false,
        canEdit: false,
        canDelete: false,
      }
    );
  }

  save(): void {
    if (this.isSaving()) return;
    this.isSaving.set(true);
    this.errorMsg.set(null);
    this.userService.updateUserPermissions(this.userId(), this.permissions()).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.successMsg.set('Permissões salvas com sucesso.');
        setTimeout(() => this.successMsg.set(null), 3000);
      },
      error: (err: { error?: { message?: string } }) => {
        this.isSaving.set(false);
        this.errorMsg.set(err?.error?.message ?? 'Erro ao salvar permissões.');
      },
    });
  }
}
