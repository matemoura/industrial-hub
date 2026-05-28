import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  MrpSuggestion,
  PlanningService,
  TimelineEntry,
} from '../planning.service';

interface TimelineBar {
  entry: TimelineEntry;
  row: number;
  colStart: number;
  colEnd: number;
}

@Component({
  selector: 'app-planning-timeline',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './planning-timeline.component.html',
  styleUrl: './planning-timeline.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanningTimelineComponent implements OnInit {
  private readonly service = inject(PlanningService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly familyCode = signal('');
  readonly entries = signal<TimelineEntry[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly weeks = signal(8);
  readonly weekOffset = signal(0);  // semanas a avançar (← →)

  readonly selectedEntry = signal<TimelineEntry | null>(null);

  // Action feedback
  readonly actionLoading = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly isSupervisor = computed(() => {
    const r = this.auth.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  readonly weekHeaders = computed(() => {
    const today = new Date();
    const offset = this.weekOffset();
    return Array.from({ length: this.weeks() }, (_, i) => {
      const d = new Date(today);
      d.setDate(d.getDate() + (i + offset) * 7);
      return this.formatWeek(d);
    });
  });

  readonly bars = computed((): TimelineBar[] => {
    const today = new Date();
    const offset = this.weekOffset();
    const weekCount = this.weeks();

    return this.entries()
      .map((entry, i) => {
        if (!entry.startDate || !entry.dueDate) return null;
        const start = new Date(entry.startDate);
        const end = new Date(entry.dueDate);
        const colStart = this.dateToWeekCol(start, today, offset);
        const colEnd = this.dateToWeekCol(end, today, offset) + 1;
        if (colEnd < 2 || colStart > weekCount + 1) return null;
        return {
          entry,
          row: i + 2,
          colStart: Math.max(2, colStart),
          colEnd: Math.min(weekCount + 2, colEnd),
        } satisfies TimelineBar;
      })
      .filter((b): b is TimelineBar => b !== null);
  });

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('familyCode') ?? '';
    this.familyCode.set(code);
    this.loadTimeline();
  }

  loadTimeline(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getTimeline(this.familyCode(), this.weeks())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.entries.set(data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar timeline.');
          this.loading.set(false);
        },
      });
  }

  prevWeeks(): void {
    this.weekOffset.update((o) => o - 1);
  }

  nextWeeks(): void {
    this.weekOffset.update((o) => o + 1);
  }

  openPanel(entry: TimelineEntry): void {
    this.selectedEntry.set(entry);
    this.actionError.set(null);
  }

  closePanel(): void {
    this.selectedEntry.set(null);
    this.actionError.set(null);
  }

  acceptSuggestion(entry: TimelineEntry): void {
    if (!entry.isMrpSuggestion) return;
    // id is encoded as "MRP-{uuid8}" — we need to find it from the list
    // In practice the backend returns the suggestion id; here we call accept by searching
    this.actionLoading.set(true);
    const suggestion = this.findSuggestionFromEntry(entry);
    if (!suggestion) { this.actionLoading.set(false); return; }
    this.service
      .acceptSuggestion(suggestion.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.closePanel();
          this.loadTimeline();
        },
        error: (err) => {
          this.actionError.set(err.error?.message ?? 'Erro ao aceitar sugestão.');
          this.actionLoading.set(false);
        },
      });
  }

  rejectSuggestion(entry: TimelineEntry, reason: string): void {
    this.actionLoading.set(true);
    const suggestion = this.findSuggestionFromEntry(entry);
    if (!suggestion) { this.actionLoading.set(false); return; }
    this.service
      .rejectSuggestion(suggestion.id, reason || 'Rejeitado pelo SUPERVISOR')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.closePanel();
          this.loadTimeline();
        },
        error: (err) => {
          this.actionError.set(err.error?.message ?? 'Erro ao rejeitar sugestão.');
          this.actionLoading.set(false);
        },
      });
  }

  barColor(entry: TimelineEntry): string {
    return entry.isMrpSuggestion ? '#F97316' : '#0099B8';
  }

  private dateToWeekCol(date: Date, today: Date, offset: number): number {
    const msPerWeek = 7 * 24 * 60 * 60 * 1000;
    const diffWeeks = Math.floor((date.getTime() - today.getTime()) / msPerWeek) - offset;
    return diffWeeks + 2; // col 1 = row labels, col 2 = week 1
  }

  private formatWeek(date: Date): string {
    return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`;
  }

  private findSuggestionFromEntry(entry: TimelineEntry): { id: string } | null {
    // BUG-2 fix: use the full suggestionId UUID provided by the backend
    if (!entry.isMrpSuggestion || !entry.suggestionId) return null;
    return { id: entry.suggestionId };
  }
}
