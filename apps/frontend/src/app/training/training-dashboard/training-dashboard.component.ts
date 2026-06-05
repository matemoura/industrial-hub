import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TrainingComplianceSummary, TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-training-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './training-dashboard.component.html',
  styleUrl: './training-dashboard.component.scss',
})
export class TrainingDashboardComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  summary = signal<TrainingComplianceSummary | null>(null);
  loading = signal(false);
  runningAlerts = signal(false);
  alertToast = signal<string | null>(null);

  readonly gaugePercent = computed(() => Math.round(this.summary()?.compliancePercent ?? 0));

  readonly gaugeDash = computed(() => {
    const r = 54;
    const circ = 2 * Math.PI * r;
    const fill = (this.gaugePercent() / 100) * circ;
    return `${fill} ${circ - fill}`;
  });

  readonly gaugeColor = computed(() => {
    const p = this.gaugePercent();
    if (p >= 80) return '#3FA66A';
    if (p >= 60) return '#E8A93C';
    return '#D24A4A';
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.trainingService.getComplianceSummary().subscribe({
      next: (s) => { this.summary.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  runAlerts(): void {
    this.runningAlerts.set(true);
    this.trainingService.runAlertsNow().subscribe({
      next: (res) => {
        this.runningAlerts.set(false);
        this.alertToast.set(`${res.alerted} notificação(ões) enviada(s).`);
        setTimeout(() => this.alertToast.set(null), 5000);
      },
      error: () => {
        this.runningAlerts.set(false);
        this.alertToast.set('Erro ao rodar alertas.');
        setTimeout(() => this.alertToast.set(null), 5000);
      },
    });
  }
}
