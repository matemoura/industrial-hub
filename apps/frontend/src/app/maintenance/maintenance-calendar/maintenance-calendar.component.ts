import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MaintenanceService, ScheduleResponse } from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

interface CalendarDay {
  date: Date;
  inCurrentMonth: boolean;
  schedules: ScheduleResponse[];
}

@Component({
  selector: 'app-maintenance-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './maintenance-calendar.component.html',
  styleUrl: './maintenance-calendar.component.scss',
})
export class MaintenanceCalendarComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);

  readonly role = this.authService.role;

  schedules = signal<ScheduleResponse[]>([]);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  selectedSchedule = signal<ScheduleResponse | null>(null);
  deactivating = signal(false);

  // Current month/year being displayed
  currentYear = signal(new Date().getFullYear());
  currentMonth = signal(new Date().getMonth()); // 0-indexed

  readonly monthLabel = computed(() => {
    const d = new Date(this.currentYear(), this.currentMonth(), 1);
    return d.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  });

  readonly calendarWeeks = computed(() => this.buildCalendar());

  readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');

  ngOnInit(): void {
    this.loadSchedules();
  }

  loadSchedules(): void {
    this.loading.set(true);
    this.maintenanceService.listSchedules().subscribe({
      next: (list) => {
        this.schedules.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar planos.');
        this.loading.set(false);
      },
    });
  }

  prevMonth(): void {
    const m = this.currentMonth();
    if (m === 0) {
      this.currentYear.update((y) => y - 1);
      this.currentMonth.set(11);
    } else {
      this.currentMonth.update((v) => v - 1);
    }
  }

  nextMonth(): void {
    const m = this.currentMonth();
    if (m === 11) {
      this.currentYear.update((y) => y + 1);
      this.currentMonth.set(0);
    } else {
      this.currentMonth.update((v) => v + 1);
    }
  }

  selectSchedule(s: ScheduleResponse): void {
    this.selectedSchedule.set(s);
  }

  closePanel(): void {
    this.selectedSchedule.set(null);
  }

  deactivate(s: ScheduleResponse): void {
    if (!confirm(`Desativar o plano "${s.title}"?`)) return;
    this.deactivating.set(true);
    this.maintenanceService.deactivateSchedule(s.id).subscribe({
      next: () => {
        this.deactivating.set(false);
        this.selectedSchedule.set(null);
        this.loadSchedules();
      },
      error: () => {
        this.deactivating.set(false);
      },
    });
  }

  /**
   * Builds a 6-week grid (42 cells) for the current month.
   * Calculates which schedules fall on each day based on recurrence + nextRunAt.
   */
  private buildCalendar(): CalendarDay[][] {
    const year = this.currentYear();
    const month = this.currentMonth();
    const allSchedules = this.schedules();

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);

    // Start grid on Monday of the week containing the 1st
    let startDate = new Date(firstDay);
    const dow = startDate.getDay(); // 0=Sun,1=Mon...
    const offset = dow === 0 ? 6 : dow - 1; // shift to Monday-first
    startDate.setDate(startDate.getDate() - offset);

    const weeks: CalendarDay[][] = [];
    let current = new Date(startDate);

    for (let w = 0; w < 6; w++) {
      const week: CalendarDay[] = [];
      for (let d = 0; d < 7; d++) {
        const day = new Date(current);
        week.push({
          date: day,
          inCurrentMonth: day.getMonth() === month,
          schedules: allSchedules.filter((s) => this.scheduleFallsOnDate(s, day)),
        });
        current.setDate(current.getDate() + 1);
      }
      weeks.push(week);
    }

    return weeks;
  }

  /**
   * Determines if a schedule would execute on a given date, based on recurrence.
   */
  scheduleFallsOnDate(schedule: ScheduleResponse, date: Date): boolean {
    if (!schedule.active) return false;

    const nextRun = new Date(schedule.nextRunAt + 'T00:00:00');

    switch (schedule.recurrence) {
      case 'DAILY':
        return date >= nextRun;

      case 'WEEKLY': {
        // 1=Mon ... 7=Sun → JS: 1=Mon ... 0=Sun
        const jsDay = schedule.dayOfWeek === 7 ? 0 : (schedule.dayOfWeek ?? 1);
        return date.getDay() === jsDay && date >= nextRun;
      }

      case 'MONTHLY': {
        const dom = schedule.dayOfMonth ?? 1;
        const lastDayOfMonth = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
        const effectiveDom = Math.min(dom, lastDayOfMonth);
        return date.getDate() === effectiveDom && date >= nextRun;
      }

      default:
        return false;
    }
  }

  formatDate(date: Date): string {
    return date.getDate().toString();
  }

  isToday(date: Date): boolean {
    const today = new Date();
    return (
      date.getFullYear() === today.getFullYear() &&
      date.getMonth() === today.getMonth() &&
      date.getDate() === today.getDate()
    );
  }
}
