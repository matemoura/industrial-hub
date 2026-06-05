import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TrainingCourse, TrainingCategory, TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-training-course-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule],
  templateUrl: './training-course-list.component.html',
  styleUrl: './training-course-list.component.scss',
})
export class TrainingCourseListComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.authService.role;
  readonly isAdmin = computed(() => this.role() === 'ADMIN');

  courses = signal<TrainingCourse[]>([]);
  loading = signal(false);
  toast = signal<string | null>(null);

  filterCategory = signal<TrainingCategory | ''>('');
  filterActive = signal<'true' | 'false' | ''>('');

  totalPages = signal(0);
  currentPage = signal(0);

  readonly categories: TrainingCategory[] = ['GMP', 'QUALITY', 'SAFETY', 'REGULATORY', 'TECHNICAL', 'OTHER'];

  readonly categoryLabels: Record<TrainingCategory, string> = {
    GMP: 'GMP',
    QUALITY: 'Qualidade',
    SAFETY: 'Segurança',
    REGULATORY: 'Regulatório',
    TECHNICAL: 'Técnico',
    OTHER: 'Outro',
  };

  readonly categoryColors: Record<TrainingCategory, string> = {
    GMP: '#56A4BB',
    QUALITY: '#3FA66A',
    SAFETY: '#E8A93C',
    REGULATORY: '#5F88A1',
    TECHNICAL: '#9CE5EE',
    OTHER: '#818286',
  };

  readonly filtered = computed(() =>
    this.courses().filter((c) => {
      const catOk = !this.filterCategory() || c.category === this.filterCategory();
      const activeOk =
        this.filterActive() === '' ||
        (this.filterActive() === 'true' && c.active) ||
        (this.filterActive() === 'false' && !c.active);
      return catOk && activeOk;
    }),
  );

  ngOnInit(): void {
    const state = history.state as { toast?: string };
    if (state?.toast) {
      this.toast.set(state.toast);
      setTimeout(() => this.toast.set(null), 4000);
    }
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.trainingService.getCourses(page, 100).subscribe({
      next: (res) => {
        this.courses.set(res.content);
        this.totalPages.set(res.totalPages);
        this.currentPage.set(res.number);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  goTo(course: TrainingCourse): void {
    void this.router.navigate(['/training/courses', course.id, 'edit']);
  }

  newCourse(): void {
    void this.router.navigate(['/training/courses/new']);
  }
}
