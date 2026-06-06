import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import {
  CalibrationRecord,
  CalibrationSchedule,
  CalibrationService,
  CalibrationSummary,
} from '../../calibration.service';
import { MaintenanceService, EquipmentResponse } from '../../maintenance.service';

@Component({
  selector: 'app-calibration-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, SlicePipe],
  templateUrl: './calibration-list.component.html',
  styleUrl: './calibration-list.component.scss',
})
export class CalibrationListComponent implements OnInit {
  private readonly calibrationService = inject(CalibrationService);
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');
  readonly isSupervisor = computed(
    () => this.authService.role() === 'SUPERVISOR' || this.authService.role() === 'ADMIN',
  );

  schedules = signal<CalibrationSchedule[]>([]);
  summary = signal<CalibrationSummary | null>(null);
  loading = signal(false);
  loadingRecords = signal(false);
  selectedScheduleId = signal<string | null>(null);
  records = signal<CalibrationRecord[]>([]);
  onlyOverdue = signal(false);
  toast = signal<string | null>(null);

  // Equipment list for "Novo Plano" form
  equipments = signal<EquipmentResponse[]>([]);

  // New plan form
  showNewPlanForm = signal(false);
  planEquipmentId = signal('');
  planIntervalDays = signal<number>(30);
  planExternalProvider = signal('');
  savingPlan = signal(false);
  planError = signal<string | null>(null);

  // Record form
  showRecordForm = signal(false);
  recordScheduleId = signal('');
  recordCalibratedAt = signal('');
  recordResult = signal<'IN_TOLERANCE' | 'OUT_OF_TOLERANCE' | 'ADJUSTED'>('IN_TOLERANCE');
  recordTechnician = signal('');
  recordNotes = signal('');
  recordCertMode = signal<'none' | 'ged' | 'upload'>('none');
  recordCertDocumentId = signal('');
  recordCertFile = signal<File | null>(null);
  savingRecord = signal(false);
  recordError = signal<string | null>(null);

  readonly filteredSchedules = computed(() => {
    const list = this.schedules();
    return this.onlyOverdue() ? list.filter((s) => s.overdue) : list;
  });

  readonly selectedSchedule = computed(() =>
    this.schedules().find((s) => s.id === this.selectedScheduleId()) ?? null,
  );

  ngOnInit(): void {
    this.loadSummary();
    this.loadSchedules();
    this.loadEquipments();
  }

  private loadSummary(): void {
    this.calibrationService.getCalibrationSummary().subscribe({
      next: (s) => this.summary.set(s),
      error: () => {},
    });
  }

  loadSchedules(): void {
    this.loading.set(true);
    this.calibrationService.listSchedules().subscribe({
      next: (list) => {
        this.schedules.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadEquipments(): void {
    this.maintenanceService.listEquipment().subscribe({
      next: (list) => this.equipments.set(list),
      error: () => {},
    });
  }

  selectSchedule(schedule: CalibrationSchedule): void {
    if (this.selectedScheduleId() === schedule.id) {
      this.selectedScheduleId.set(null);
      this.records.set([]);
      return;
    }
    this.selectedScheduleId.set(schedule.id);
    this.loadingRecords.set(true);
    this.calibrationService.listRecords(schedule.id).subscribe({
      next: (r) => {
        this.records.set(r);
        this.loadingRecords.set(false);
      },
      error: () => this.loadingRecords.set(false),
    });
  }

  dueSoonClass(s: CalibrationSchedule): string {
    if (s.overdue) return 'chip chip--danger';
    const dueDate = new Date(s.nextDueAt);
    const in14 = new Date();
    in14.setDate(in14.getDate() + 14);
    if (dueDate <= in14) return 'chip chip--warn';
    return 'chip chip--ok';
  }

  openNewPlanForm(): void {
    this.planEquipmentId.set('');
    this.planIntervalDays.set(30);
    this.planExternalProvider.set('');
    this.planError.set(null);
    this.showNewPlanForm.set(true);
  }

  submitNewPlan(): void {
    if (!this.planEquipmentId() || this.planIntervalDays() < 1) return;
    this.planError.set(null);
    this.savingPlan.set(true);
    this.calibrationService
      .createSchedule({
        equipmentId: this.planEquipmentId(),
        intervalDays: this.planIntervalDays(),
        externalProvider: this.planExternalProvider() || undefined,
      })
      .subscribe({
        next: (s) => {
          this.schedules.update((list) => [...list, s]);
          this.showNewPlanForm.set(false);
          this.savingPlan.set(false);
          this.loadSummary();
          this.showToast('Plano de calibração criado.');
        },
        error: (err: { error?: { message?: string } }) => {
          this.planError.set(err?.error?.message ?? 'Erro ao criar plano.');
          this.savingPlan.set(false);
        },
      });
  }

  openRecordForm(schedule: CalibrationSchedule): void {
    this.recordScheduleId.set(schedule.id);
    this.recordCalibratedAt.set('');
    this.recordResult.set('IN_TOLERANCE');
    this.recordTechnician.set('');
    this.recordNotes.set('');
    this.recordCertMode.set('none');
    this.recordCertDocumentId.set('');
    this.recordCertFile.set(null);
    this.recordError.set(null);
    this.showRecordForm.set(true);
  }

  onCertFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.recordCertFile.set(input.files?.[0] ?? null);
  }

  submitRecord(): void {
    this.recordError.set(null);
    const fd = new FormData();
    fd.append('scheduleId', this.recordScheduleId());
    fd.append('calibratedAt', this.recordCalibratedAt());
    fd.append('result', this.recordResult());
    fd.append('technician', this.recordTechnician());
    if (this.recordNotes()) fd.append('notes', this.recordNotes());
    if (this.recordCertMode() === 'ged' && this.recordCertDocumentId()) {
      fd.append('certificateDocumentId', this.recordCertDocumentId());
    }
    if (this.recordCertMode() === 'upload' && this.recordCertFile()) {
      fd.append('certificate', this.recordCertFile()!);
    }

    this.savingRecord.set(true);
    this.calibrationService.createRecord(fd).subscribe({
      next: (r) => {
        if (this.selectedScheduleId() === this.recordScheduleId()) {
          this.records.update((list) => [r, ...list]);
        }
        this.showRecordForm.set(false);
        this.savingRecord.set(false);
        this.loadSchedules();
        this.loadSummary();
        const msg =
          r.result === 'OUT_OF_TOLERANCE'
            ? 'Registro criado. NC gerada automaticamente.'
            : 'Registro de calibração criado.';
        this.showToast(msg);
      },
      error: (err: { error?: { message?: string } }) => {
        this.recordError.set(err?.error?.message ?? 'Erro ao registrar calibração.');
        this.savingRecord.set(false);
      },
    });
  }

  deactivate(schedule: CalibrationSchedule): void {
    if (!confirm(`Desativar plano de calibração para ${schedule.equipmentName}?`)) return;
    this.calibrationService.deactivateSchedule(schedule.id).subscribe({
      next: () => {
        this.schedules.update((list) => list.filter((s) => s.id !== schedule.id));
        if (this.selectedScheduleId() === schedule.id) {
          this.selectedScheduleId.set(null);
          this.records.set([]);
        }
        this.loadSummary();
        this.showToast('Plano desativado.');
      },
      error: (err: { error?: { message?: string } }) =>
        this.showToast(err?.error?.message ?? 'Erro ao desativar.'),
    });
  }

  downloadCertificate(record: CalibrationRecord): void {
    this.calibrationService.getCertificateUrl(record.id).subscribe({
      next: (res) => window.open(res.url, '_blank', 'noopener,noreferrer'),
      error: () => this.showToast('Erro ao obter URL do certificado.'),
    });
  }

  resultLabel(result: CalibrationRecord['result']): string {
    switch (result) {
      case 'IN_TOLERANCE': return 'Dentro do tolerável';
      case 'OUT_OF_TOLERANCE': return 'Fora do tolerável';
      case 'ADJUSTED': return 'Ajustado';
    }
  }

  resultClass(result: CalibrationRecord['result']): string {
    switch (result) {
      case 'IN_TOLERANCE': return 'chip chip--ok';
      case 'OUT_OF_TOLERANCE': return 'chip chip--danger';
      case 'ADJUSTED': return 'chip chip--warn';
    }
  }

  private showToast(msg: string): void {
    this.toast.set(msg);
    setTimeout(() => this.toast.set(null), 4000);
  }
}
