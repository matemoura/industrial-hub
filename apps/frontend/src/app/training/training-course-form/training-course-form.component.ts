import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TrainingCategory, CreateCourseRequest, TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';

const ALL_ROLES = ['OPERATOR', 'SUPERVISOR', 'ADMIN'] as const;

@Component({
  selector: 'app-training-course-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './training-course-form.component.html',
  styleUrl: './training-course-form.component.scss',
})
export class TrainingCourseFormComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  courseId = signal<string | null>(null);
  readonly isEdit = computed(() => !!this.courseId());

  saving = signal(false);
  deactivating = signal(false);
  error = signal<string | null>(null);
  loading = signal(false);

  code = signal('');
  title = signal('');
  description = signal('');
  category = signal<TrainingCategory>('GMP');
  durationHours = signal<number>(4);
  validityMonths = signal<number | null>(null);
  selectedRoles = signal<Set<string>>(new Set());

  readonly allRoles = ALL_ROLES;
  readonly categories: TrainingCategory[] = ['GMP', 'QUALITY', 'SAFETY', 'REGULATORY', 'TECHNICAL', 'OTHER'];

  readonly categoryLabels: Record<TrainingCategory, string> = {
    GMP: 'GMP',
    QUALITY: 'Qualidade',
    SAFETY: 'Segurança',
    REGULATORY: 'Regulatório',
    TECHNICAL: 'Técnico',
    OTHER: 'Outro',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.courseId.set(id);
      this.loadCourse(id);
    }
  }

  private loadCourse(id: string): void {
    this.loading.set(true);
    this.trainingService.getCourse(id).subscribe({
      next: (c) => {
        this.code.set(c.code);
        this.title.set(c.title);
        this.description.set(c.description ?? '');
        this.category.set(c.category);
        this.durationHours.set(c.durationHours);
        this.validityMonths.set(c.validityMonths ?? null);
        this.selectedRoles.set(new Set(c.requiredForRoles));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  toggleRole(role: string): void {
    this.selectedRoles.update((prev) => {
      const next = new Set(prev);
      next.has(role) ? next.delete(role) : next.add(role);
      return next;
    });
  }

  hasRole(role: string): boolean {
    return this.selectedRoles().has(role);
  }

  save(): void {
    this.error.set(null);
    const req: CreateCourseRequest = {
      code: this.code(),
      title: this.title(),
      description: this.description() || undefined,
      category: this.category(),
      durationHours: this.durationHours(),
      validityMonths: this.validityMonths() ?? undefined,
      requiredForRoles: [...this.selectedRoles()],
    };

    this.saving.set(true);
    const { code: _code, ...updateReq } = req;
    const obs$ = this.isEdit()
      ? this.trainingService.updateCourse(this.courseId()!, updateReq)
      : this.trainingService.createCourse(req);

    obs$.subscribe({
      next: () => {
        void this.router.navigate(['/training/courses'], {
          state: { toast: this.isEdit() ? 'Curso atualizado com sucesso.' : 'Curso criado com sucesso.' },
        });
      },
      error: (err: { error?: { message?: string } }) => {
        this.error.set(err?.error?.message ?? 'Erro ao salvar curso.');
        this.saving.set(false);
      },
    });
  }

  deactivate(): void {
    if (!this.courseId()) return;
    this.deactivating.set(true);
    this.trainingService.deactivateCourse(this.courseId()!).subscribe({
      next: () => {
        void this.router.navigate(['/training/courses'], { state: { toast: 'Curso desativado.' } });
      },
      error: (err: { error?: { message?: string } }) => {
        this.error.set(err?.error?.message ?? 'Erro ao desativar.');
        this.deactivating.set(false);
      },
    });
  }

  cancel(): void {
    void this.router.navigate(['/training/courses']);
  }
}
