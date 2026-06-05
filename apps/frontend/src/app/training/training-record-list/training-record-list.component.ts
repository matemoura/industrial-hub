import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrainingRecord, TrainingCourse, TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-training-record-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, SlicePipe],
  templateUrl: './training-record-list.component.html',
  styleUrl: './training-record-list.component.scss',
})
export class TrainingRecordListComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);
  private readonly authService = inject(AuthService);

  readonly role = this.authService.role;
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');

  records = signal<TrainingRecord[]>([]);
  courses = signal<TrainingCourse[]>([]);
  loading = signal(false);
  saving = signal(false);
  toast = signal<string | null>(null);
  error = signal<string | null>(null);

  filterUsername = signal('');
  filterCourseId = signal('');
  filterPassed = signal<'true' | 'false' | ''>('');

  totalPages = signal(0);
  currentPage = signal(0);

  showNewForm = signal(false);
  showEffectivenessForm = signal<string | null>(null);

  // New record form fields
  newCourseId = signal('');
  newUsername = signal('');
  newCompletedAt = signal('');
  newInstructor = signal('');
  newScore = signal<number | null>(null);
  newPassed = signal(true);
  newCertificate = signal<File | null>(null);

  // Effectiveness form
  effResult = signal<'EFFECTIVE' | 'PARTIALLY_EFFECTIVE' | 'NOT_EFFECTIVE'>('EFFECTIVE');
  effNotes = signal('');

  ngOnInit(): void {
    this.loadCourses();
    this.loadRecords(0);
  }

  private loadCourses(): void {
    this.trainingService.getCourses(0, 200).subscribe({
      next: (res) => this.courses.set(res.content.filter((c) => c.active)),
      error: () => {},
    });
  }

  loadRecords(page: number): void {
    this.loading.set(true);
    const filters = {
      username: this.filterUsername() || undefined,
      courseId: this.filterCourseId() || undefined,
      passed: this.filterPassed() !== '' ? this.filterPassed() === 'true' : undefined,
    };
    this.trainingService.getRecords(filters, page).subscribe({
      next: (res) => {
        this.records.set(res.content);
        this.totalPages.set(res.totalPages);
        this.currentPage.set(res.number);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onCertificateChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.newCertificate.set(input.files?.[0] ?? null);
  }

  submitRecord(): void {
    this.error.set(null);
    const fd = new FormData();
    fd.append('courseId', this.newCourseId());
    fd.append('username', this.newUsername());
    fd.append('completedAt', this.newCompletedAt());
    fd.append('passed', String(this.newPassed()));
    if (this.newInstructor()) fd.append('instructorName', this.newInstructor());
    if (this.newScore() !== null) fd.append('score', String(this.newScore()));
    if (this.newCertificate()) fd.append('certificate', this.newCertificate()!);

    this.saving.set(true);
    this.trainingService.createRecord(fd).subscribe({
      next: () => {
        this.showNewForm.set(false);
        this.resetForm();
        this.loadRecords(0);
        this.showToast('Registro criado com sucesso.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.error.set(err?.error?.message ?? 'Erro ao criar registro.');
        this.saving.set(false);
      },
    });
  }

  downloadCertificate(record: TrainingRecord): void {
    this.trainingService.getCertificateUrl(record.id).subscribe({
      next: (res) => window.open(res.url, '_blank', 'noopener,noreferrer'),
      error: () => this.showToast('Erro ao obter URL do certificado.'),
    });
  }

  deleteRecord(record: TrainingRecord): void {
    if (!confirm(`Excluir registro de ${record.username} em ${record.courseCode}?`)) return;
    this.trainingService.deleteRecord(record.id).subscribe({
      next: () => {
        this.records.update((list) => list.filter((r) => r.id !== record.id));
        this.showToast('Registro excluído.');
      },
      error: (err: { error?: { message?: string } }) =>
        this.showToast(err?.error?.message ?? 'Erro ao excluir.'),
    });
  }

  openEffectivenessForm(recordId: string): void {
    this.showEffectivenessForm.set(recordId);
    this.effResult.set('EFFECTIVE');
    this.effNotes.set('');
  }

  submitEffectiveness(): void {
    const id = this.showEffectivenessForm();
    if (!id) return;
    this.saving.set(true);
    this.trainingService.assessEffectiveness(id, {
      result: this.effResult(),
      notes: this.effNotes() || undefined,
    }).subscribe({
      next: (updated) => {
        this.records.update((list) => list.map((r) => (r.id === id ? updated : r)));
        this.showEffectivenessForm.set(null);
        this.saving.set(false);
        this.showToast('Avaliação de eficácia registrada.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.error.set(err?.error?.message ?? 'Erro ao avaliar eficácia.');
        this.saving.set(false);
      },
    });
  }

  expiryClass(record: TrainingRecord): string {
    if (!record.expiresAt) return '';
    const d = new Date(record.expiresAt);
    const now = new Date();
    const in30 = new Date(); in30.setDate(in30.getDate() + 30);
    if (d < now) return 'expiry--expired';
    if (d <= in30) return 'expiry--expiring';
    return '';
  }

  private resetForm(): void {
    this.newCourseId.set('');
    this.newUsername.set('');
    this.newCompletedAt.set('');
    this.newInstructor.set('');
    this.newScore.set(null);
    this.newPassed.set(true);
    this.newCertificate.set(null);
    this.saving.set(false);
  }

  private showToast(msg: string): void {
    this.toast.set(msg);
    setTimeout(() => this.toast.set(null), 4000);
  }
}
