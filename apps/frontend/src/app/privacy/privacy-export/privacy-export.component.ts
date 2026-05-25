import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { UserService } from '../../admin/user.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-privacy-export',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './privacy-export.component.html',
  styleUrl: './privacy-export.component.scss',
})
export class PrivacyExportComponent {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);

  readonly loading = signal(false);
  readonly successMsg = signal<string | null>(null);
  readonly errorMsg = signal<string | null>(null);

  exportData(): void {
    this.loading.set(true);
    this.successMsg.set('Exportação iniciada…');
    this.errorMsg.set(null);

    this.userService.exportMyData().subscribe({
      next: (blob) => {
        const safeUsername = (this.authService.username() ?? 'usuario').replace(/[^a-zA-Z0-9_-]/g, '_');
        const filename = `meus-dados-${safeUsername}.json`;

        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);

        this.loading.set(false);
        this.successMsg.set(`Download iniciado: ${filename}`);
      },
      error: () => {
        this.loading.set(false);
        this.successMsg.set(null);
        this.errorMsg.set('Erro ao exportar dados. Tente novamente.');
      },
    });
  }
}
