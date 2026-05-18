import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  CreateSchedulePayload,
  EquipmentResponse,
  MaintenanceService,
  ScheduleRecurrence,
  UpdateSchedulePayload,
  WorkOrderPriority,
} from '../maintenance.service';

@Component({
  selector: 'app-schedule-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './schedule-form.component.html',
  styleUrl: './schedule-form.component.scss',
})
export class ScheduleFormComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  editId = signal<string | null>(null);
  get isEditMode(): boolean { return this.editId() !== null; }

  // Form fields
  equipmentId = signal('');
  title = signal('');
  description = signal('');
  priority = signal<WorkOrderPriority | ''>('');
  recurrence = signal<ScheduleRecurrence | ''>('');
  dayOfWeek = signal<number | ''>('');
  dayOfMonth = signal<number | ''>('');

  equipment = signal<EquipmentResponse[]>([]);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  readonly priorities: WorkOrderPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly recurrences: ScheduleRecurrence[] = ['DAILY', 'WEEKLY', 'MONTHLY'];
  readonly daysOfWeek = [1, 2, 3, 4, 5, 6, 7];
  readonly dayOfWeekLabels: Record<number, string> = {
    1: 'Segunda', 2: 'Terça', 3: 'Quarta', 4: 'Quinta', 5: 'Sexta', 6: 'Sábado', 7: 'Domingo',
  };

  readonly showDayOfWeek = computed(() => this.recurrence() === 'WEEKLY');
  readonly showDayOfMonth = computed(() => this.recurrence() === 'MONTHLY');

  get isValid(): boolean {
    if (!this.title().trim() || !this.priority() || !this.recurrence()) return false;
    if (!this.isEditMode && !this.equipmentId()) return false;
    if (this.recurrence() === 'WEEKLY' && !this.dayOfWeek()) return false;
    if (this.recurrence() === 'MONTHLY' && !this.dayOfMonth()) return false;
    return true;
  }

  ngOnInit(): void {
    this.maintenanceService.listEquipment().subscribe({
      next: (list) => this.equipment.set(list),
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId.set(id);
      this.loading.set(true);
      this.maintenanceService.getSchedule(id).subscribe({
        next: (s) => {
          this.title.set(s.title);
          this.description.set(s.description ?? '');
          this.priority.set(s.priority);
          this.recurrence.set(s.recurrence);
          this.dayOfWeek.set(s.dayOfWeek ?? '');
          this.dayOfMonth.set(s.dayOfMonth ?? '');
          this.loading.set(false);
        },
        error: () => {
          this.errorMsg.set('Plano não encontrado.');
          this.loading.set(false);
        },
      });
    }
  }

  onRecurrenceChange(value: string): void {
    this.recurrence.set(value as ScheduleRecurrence);
    this.dayOfWeek.set('');
    this.dayOfMonth.set('');
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;
    this.loading.set(true);
    this.errorMsg.set(null);

    const id = this.editId();
    if (id) {
      const payload: UpdateSchedulePayload = {
        title: this.title().trim(),
        description: this.description().trim() || undefined,
        priority: this.priority() as WorkOrderPriority,
        recurrence: this.recurrence() as ScheduleRecurrence,
        dayOfWeek: this.dayOfWeek() !== '' ? Number(this.dayOfWeek()) : undefined,
        dayOfMonth: this.dayOfMonth() !== '' ? Number(this.dayOfMonth()) : undefined,
      };
      this.maintenanceService.updateSchedule(id, payload).subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/maintenance/schedules'], {
            state: { toast: 'Plano atualizado com sucesso' },
          });
        },
        error: (err) => {
          this.errorMsg.set(err?.error?.message ?? 'Erro ao atualizar plano.');
          this.loading.set(false);
        },
      });
    } else {
      const payload: CreateSchedulePayload = {
        equipmentId: this.equipmentId(),
        title: this.title().trim(),
        description: this.description().trim() || undefined,
        priority: this.priority() as WorkOrderPriority,
        recurrence: this.recurrence() as ScheduleRecurrence,
        dayOfWeek: this.dayOfWeek() !== '' ? Number(this.dayOfWeek()) : undefined,
        dayOfMonth: this.dayOfMonth() !== '' ? Number(this.dayOfMonth()) : undefined,
      };
      this.maintenanceService.createSchedule(payload).subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/maintenance/schedules'], {
            state: { toast: 'Plano criado com sucesso' },
          });
        },
        error: (err) => {
          this.errorMsg.set(err?.error?.message ?? 'Erro ao criar plano.');
          this.loading.set(false);
        },
      });
    }
  }
}
