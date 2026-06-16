import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { KpiService, KpiSummaryResponse } from '../kpi.service';
import { AuthService } from '../../auth/auth.service';
import { GaugeComponent } from '../../shared/charts/gauge.component';
import { TranslatePipe } from '../../shared/i18n/translate.pipe';

@Component({
  selector: 'app-kpi-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, GaugeComponent, TranslatePipe],
  templateUrl: './kpi-dashboard.component.html',
  styleUrl: './kpi-dashboard.component.scss',
})
export class KpiDashboardComponent implements OnInit {
  private readonly kpiService = inject(KpiService);
  private readonly authService = inject(AuthService);

  readonly role      = this.authService.role;
  readonly loading   = signal(true);
  readonly error     = signal(false);
  readonly kpi       = signal<KpiSummaryResponse | null>(null);
  readonly brandLine = signal<'uro' | 'vasc'>('uro');

  private brandTimer: ReturnType<typeof setInterval> | null = null;

  readonly oeeValue = computed(() => this.kpi()?.oeeAvgLast30Days ?? 0);
  readonly oeeHasData = computed(() => (this.kpi()?.oeeAvgLast30Days ?? 0) > 0);
  readonly oeeDisplay = computed(() => {
    const v = this.kpi()?.oeeAvgLast30Days;
    return v != null && v > 0 ? (v * 100).toFixed(1) + '%' : '–';
  });
  readonly oeeOk = computed(() => (this.kpi()?.oeeAvgLast30Days ?? 0) >= 0.65);

  readonly prodOrdersOverdueAlert = computed(() => (this.kpi()?.totalProductionOrdersOverdue ?? 0) > 0);

  readonly lastSyncDisplay = computed(() => {
    const s = this.kpi()?.lastDynamicsSync;
    if (!s) return 'Nunca sincronizado';
    return new Date(s).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  });

  readonly today = new Date().toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'short', year: 'numeric',
  }).toUpperCase();

  readonly quickLinks = [
    { route: '/qms/non-conformances', kicker: 'QMS', label: 'Não-Conformidades', sub: 'ISO 13485 §8.3' },
    { route: '/maintenance/equipment', kicker: 'MANUTENÇÃO', label: 'Equipamentos e OSs', sub: 'TPM · MTTR · MTBF' },
    { route: '/production/planning-board', kicker: 'PRODUÇÃO', label: 'Planning Board', sub: 'OPs · Famílias · MRP' },
    { route: '/oee/summary', kicker: 'RESUMO', label: 'Resumo OEE', sub: 'Períodos e tendências' },
  ];

  setBrandLine(line: 'uro' | 'vasc'): void {
    this.brandLine.set(line);
    if (this.brandTimer) clearInterval(this.brandTimer);
    this.brandTimer = setInterval(() => this.brandLine.update(l => l === 'uro' ? 'vasc' : 'uro'), 5000);
  }

  ngOnInit(): void {
    this.kpiService.getSummary().subscribe({
      next:  (d) => { this.kpi.set(d); this.loading.set(false); },
      error: ()  => { this.error.set(true); this.loading.set(false); },
    });
    this.brandTimer = setInterval(() => this.brandLine.update(l => l === 'uro' ? 'vasc' : 'uro'), 5000);
  }
}
