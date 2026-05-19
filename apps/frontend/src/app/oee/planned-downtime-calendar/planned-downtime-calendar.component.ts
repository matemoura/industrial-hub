import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  CreateDowntimePayload,
  DowntimeReason,
  OeeService,
  PlannedDowntimeResponse,
} from '../oee.service';
import { AuthService } from '../../auth/auth.service';
import { MaintenanceService } from '../../maintenance/maintenance.service';

interface CalendarDay {
  date: Date;
  inCurrentMonth: boolean;
  downtimes: PlannedDowntimeResponse[];
}

interface EquipmentOption {
  id: string;
  code: string;
  name: string;
}

@Component({
  selector: 'app-planned-downtime-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './planned-downtime-calendar.component.html',
  styleUrl: './planned-downtime-calendar.component.scss',
})
export class PlannedDowntimeCalendarComponent implements OnInit {
  private readonly oeeService = inject(OeeService);
  private readonly authService = inject(AuthService);
  private readonly maintenanceService = inject(MaintenanceService);

  readonly role = this.authService.role;
  readonly isSupervisor = computed(
    () => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN',
  );

  downtimes = signal<PlannedDowntimeResponse[]>([]);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  selectedDowntime = signal<PlannedDowntimeResponse | null>(null);
  deleting = signal(false);

  showForm = signal(false);
  submitting = signal(false);
  formError = signal<string | null>(null);

  // Form fields
  formEquipmentId = signal<string>('');
  formReason = signal<DowntimeReason>('PREVENTIVE_MAINTENANCE');
  formStartAt = signal('');
  formEndAt = signal('');
  formDescription = signal('');

  // Calendar navigation
  currentYear = signal(new Date().getFullYear());
  currentMonth = signal(new Date().getMonth()); // 0-indexed

  readonly monthLabel = computed(() => {
    const d = new Date(this.currentYear(), this.currentMonth(), 1);
    return d.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  });

  readonly calendarWeeks = computed(() => this.buildCalendar());

  readonly reasonOptions: DowntimeReason[] = [
    'PREVENTIVE_MAINTENANCE',
    'SCHEDULED_SETUP',
    'HOLIDAY',
    'OTHER',
  ];

  readonly reasonLabels: Record<DowntimeReason, string> = {
    PREVENTIVE_MAINTENANCE: 'Manutenção Preventiva',
    SCHEDULED_SETUP: 'Setup Programado',
    HOLIDAY: 'Feriado / Recesso',
    OTHER: 'Outro',
  };

  // Populated on init via API; not derived from downtimes
  readonly equipmentOptions = signal<EquipmentOption[]>([]);
  equipmentOptionsError = signal(false);

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    forkJoin([
      this.oeeService.listDowntimes(),
      this.maintenanceService.listEquipment(),
    ]).subscribe({
      next: ([downtimes, equipment]) => {
        this.downtimes.set(downtimes);
        this.equipmentOptions.set(
          equipment.map((eq) => ({ id: eq.id, code: eq.code, name: eq.name })),
        );
        this.loading.set(false);
      },
      error: () => {
        // Try loading only downtimes if joint call fails
        this.maintenanceService.listEquipment().subscribe({
          next: (equipment) =>
            this.equipmentOptions.set(
              equipment.map((eq) => ({ id: eq.id, code: eq.code, name: eq.name })),
            ),
          error: () => {
            this.equipmentOptions.set([]);
            this.equipmentOptionsError.set(true);
          },
        });
        this.oeeService.listDowntimes().subscribe({
          next: (list) => {
            this.downtimes.set(list);
            this.loading.set(false);
          },
          error: () => {
            this.errorMsg.set('Erro ao carregar paradas planejadas.');
            this.loading.set(false);
          },
        });
      },
    });
  }

  loadDowntimes(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.oeeService.listDowntimes().subscribe({
      next: (list) => {
        this.downtimes.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar paradas planejadas.');
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

  selectDowntime(d: PlannedDowntimeResponse): void {
    this.selectedDowntime.set(d);
    this.showForm.set(false);
  }

  closePanel(): void {
    this.selectedDowntime.set(null);
  }

  deleteDowntime(d: PlannedDowntimeResponse): void {
    if (!confirm(`Excluir parada planejada de "${this.reasonLabels[d.reason]}"?`)) return;
    this.deleting.set(true);
    this.oeeService.deleteDowntime(d.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.selectedDowntime.set(null);
        this.loadDowntimes();
      },
      error: () => {
        this.deleting.set(false);
      },
    });
  }

  openForm(): void {
    this.showForm.set(true);
    this.selectedDowntime.set(null);
    this.formEquipmentId.set('');
    this.formReason.set('PREVENTIVE_MAINTENANCE');
    this.formStartAt.set('');
    this.formEndAt.set('');
    this.formDescription.set('');
    this.formError.set(null);
  }

  closeForm(): void {
    this.showForm.set(false);
  }

  submitForm(): void {
    if (!this.formStartAt() || !this.formEndAt()) {
      this.formError.set('Preencha os campos obrigatórios.');
      return;
    }
    const payload: CreateDowntimePayload = {
      equipmentId: this.formEquipmentId() || null,
      reason: this.formReason(),
      startAt: this.formStartAt(),
      endAt: this.formEndAt(),
      ...(this.formDescription() ? { description: this.formDescription() } : {}),
    };
    this.submitting.set(true);
    this.formError.set(null);
    this.oeeService.createDowntime(payload).subscribe({
      next: () => {
        this.submitting.set(false);
        this.showForm.set(false);
        this.loadDowntimes();
      },
      error: () => {
        this.formError.set('Erro ao registrar parada. Tente novamente.');
        this.submitting.set(false);
      },
    });
  }

  reasonBadgeColor(reason: DowntimeReason): string {
    const map: Record<DowntimeReason, string> = {
      PREVENTIVE_MAINTENANCE: '#0099B8',
      SCHEDULED_SETUP: '#F97316',
      HOLIDAY: '#8B5CF6',
      OTHER: '#64748B',
    };
    return map[reason] ?? '#0099B8';
  }

  formatDate(date: Date): string {
    return date.getDate().toString();
  }

  formatDateTime(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('pt-BR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }

  isToday(date: Date): boolean {
    const today = new Date();
    return (
      date.getFullYear() === today.getFullYear() &&
      date.getMonth() === today.getMonth() &&
      date.getDate() === today.getDate()
    );
  }

  private buildCalendar(): CalendarDay[][] {
    const year = this.currentYear();
    const month = this.currentMonth();
    const allDowntimes = this.downtimes();

    const firstDay = new Date(year, month, 1);

    // Start grid on Monday of the week containing the 1st
    let startDate = new Date(firstDay);
    const dow = startDate.getDay(); // 0=Sun,1=Mon...
    const offset = dow === 0 ? 6 : dow - 1;
    startDate.setDate(startDate.getDate() - offset);

    const weeks: CalendarDay[][] = [];
    const current = new Date(startDate);

    for (let w = 0; w < 6; w++) {
      const week: CalendarDay[] = [];
      for (let d = 0; d < 7; d++) {
        const day = new Date(current);
        week.push({
          date: day,
          inCurrentMonth: day.getMonth() === month,
          downtimes: allDowntimes.filter((dt) => this.downtimeFallsOnDate(dt, day)),
        });
        current.setDate(current.getDate() + 1);
      }
      weeks.push(week);
    }

    return weeks;
  }

  /**
   * Returns true if the downtime interval includes the given calendar day.
   */
  downtimeFallsOnDate(downtime: PlannedDowntimeResponse, date: Date): boolean {
    const start = new Date(downtime.startAt);
    const end = new Date(downtime.endAt);
    const dayStart = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const dayEnd = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999);
    return start <= dayEnd && end >= dayStart;
  }
}
