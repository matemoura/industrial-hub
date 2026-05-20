import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MaintenanceService, SparePartResponse } from '../maintenance.service';

@Component({
  selector: 'app-maintenance-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './maintenance-dashboard.component.html',
  styleUrl: './maintenance-dashboard.component.scss',
})
export class MaintenanceDashboardComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly destroyRef = inject(DestroyRef);

  readonly criticalParts = signal<SparePartResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMsg = signal<string | null>(null);

  ngOnInit(): void {
    this.loadCriticalStock();
  }

  loadCriticalStock(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.maintenanceService
      .listBelowMin()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.criticalParts.set(list);
          this.loading.set(false);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar estoque crítico.');
          this.loading.set(false);
        },
      });
  }
}
