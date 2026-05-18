import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { QmsService, SupplierResponse, SupplierQualityScore } from '../qms.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-supplier-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './supplier-detail.component.html',
  styleUrl: './supplier-detail.component.scss',
})
export class SupplierDetailComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly role = this.authService.role;

  supplier = signal<SupplierResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);

  score = signal<SupplierQualityScore | null>(null);
  scoreLoading = signal(false);
  scoreDays = signal(90);
  toast = signal<string | null>(null);

  deactivating = signal(false);

  readonly scoreDayOptions = [30, 90, 180];

  readonly scoreColor = computed(() => {
    const s = this.score();
    if (!s) return 'neutral';
    if (s.qualityScore >= 80) return 'green';
    if (s.qualityScore >= 60) return 'amber';
    return 'red';
  });

  get isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    const toastMsg = (history.state as { toast?: string })?.toast;
    if (toastMsg) {
      this.toast.set(toastMsg);
      setTimeout(() => this.toast.set(null), 4000);
    }

    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadSupplier(id);
  }

  loadSupplier(id: string): void {
    this.loading.set(true);
    this.qmsService.getSupplier(id).subscribe({
      next: (s) => {
        this.supplier.set(s);
        this.loading.set(false);
        if (this.isSupervisor) {
          this.loadScore(id, this.scoreDays());
        }
      },
      error: () => {
        this.errorMsg.set('Fornecedor não encontrado.');
        this.loading.set(false);
      },
    });
  }

  loadScore(id: string, days: number): void {
    this.scoreLoading.set(true);
    this.qmsService.getSupplierQualityScore(id, days).subscribe({
      next: (s) => {
        this.score.set(s);
        this.scoreLoading.set(false);
      },
      error: () => {
        this.score.set(null);
        this.scoreLoading.set(false);
      },
    });
  }

  changeScorePeriod(days: number): void {
    const s = this.supplier();
    if (!s) return;
    this.scoreDays.set(days);
    this.loadScore(s.id, days);
  }

  deactivate(): void {
    const s = this.supplier();
    if (!s || !confirm(`Desativar o fornecedor "${s.name}"? Esta ação não pode ser desfeita facilmente.`)) return;

    this.deactivating.set(true);
    this.qmsService.deactivateSupplier(s.id).subscribe({
      next: () => {
        this.deactivating.set(false);
        this.router.navigate(['/qms/suppliers'], {
          state: { toast: `Fornecedor "${s.name}" desativado com sucesso` },
        });
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao desativar fornecedor.');
        this.deactivating.set(false);
      },
    });
  }

  formatScore(value: number): string {
    return value.toFixed(1);
  }
}
