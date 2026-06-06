import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { SlicePipe } from '@angular/common';
import { AuthService } from '../../../auth/auth.service';
import {
  CalibrationSchedule,
  CalibrationService,
  CalibrationSummary,
} from '../../calibration.service';

interface ScheduleGroup {
  month: string;
  schedules: CalibrationSchedule[];
}

@Component({
  selector: 'app-calibration-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SlicePipe],
  templateUrl: './calibration-dashboard.component.html',
  styleUrl: './calibration-dashboard.component.scss',
})
export class CalibrationDashboardComponent implements OnInit {
  private readonly calibrationService = inject(CalibrationService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  summary = signal<CalibrationSummary | null>(null);
  schedules = signal<CalibrationSchedule[]>([]);
  loading = signal(false);
  runningAlerts = signal(false);
  alertToast = signal<string | null>(null);

  /** Agrupa schedules por YYYY-MM de nextDueAt */
  readonly grouped = computed<ScheduleGroup[]>(() => {
    const map = new Map<string, CalibrationSchedule[]>();
    for (const s of this.schedules()) {
      const month = s.nextDueAt.slice(0, 7);
      const group = map.get(month) ?? [];
      group.push(s);
      map.set(month, group);
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([month, items]) => ({ month, schedules: items }));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.calibrationService.getCalibrationSummary().subscribe({
      next: (s) => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.calibrationService.listSchedules().subscribe({
      next: (list) => this.schedules.set(list),
      error: () => {},
    });
  }

  runAlerts(): void {
    this.runningAlerts.set(true);
    this.calibrationService.runAlertsNow().subscribe({
      next: (res) => {
        this.runningAlerts.set(false);
        this.alertToast.set(`${res.alertsSent} notificação(ões) enviada(s).`);
        setTimeout(() => this.alertToast.set(null), 5000);
      },
      error: () => {
        this.runningAlerts.set(false);
        this.alertToast.set('Erro ao rodar alertas.');
        setTimeout(() => this.alertToast.set(null), 5000);
      },
    });
  }

  dueSoonClass(s: CalibrationSchedule): string {
    if (s.overdue) return 'chip chip--danger';
    const due = new Date(s.nextDueAt);
    const in14 = new Date();
    in14.setDate(in14.getDate() + 14);
    if (due <= in14) return 'chip chip--warn';
    return 'chip chip--ok';
  }

  formatMonth(yyyyMM: string): string {
    const [year, month] = yyyyMM.split('-');
    const date = new Date(+year, +month - 1, 1);
    return date.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  }
}
