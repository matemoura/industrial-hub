import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MaintenanceService, ScheduleRecurrence, ScheduleResponse } from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-schedule-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './schedule-list.component.html',
  styleUrl: './schedule-list.component.scss',
})
export class ScheduleListComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);

  readonly role = this.authService.role;

  schedules = signal<ScheduleResponse[]>([]);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');

  readonly recurrenceLabels: Record<ScheduleRecurrence, string> = {
    DAILY: 'Diária',
    WEEKLY: 'Semanal',
    MONTHLY: 'Mensal',
  };

  readonly dayNames = ['', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

  ngOnInit(): void {
    this.loadList();
  }

  loadList(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.maintenanceService.listSchedules().subscribe({
      next: (list) => {
        this.schedules.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar planos de manutenção.');
        this.loading.set(false);
      },
    });
  }

  recurrenceDetail(s: ScheduleResponse): string {
    switch (s.recurrence) {
      case 'WEEKLY':
        return `Semanal — ${this.dayNames[s.dayOfWeek ?? 0]}`;
      case 'MONTHLY':
        return `Mensal — dia ${s.dayOfMonth}`;
      default:
        return 'Diária';
    }
  }
}
