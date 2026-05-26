import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  DashboardService,
  SUPERVISOR_ONLY_WIDGETS,
  WIDGET_LABELS,
  WidgetConfig,
  WidgetType,
} from '../dashboard.service';
import { AuthService } from '../../auth/auth.service';
import { OeeAvgWidgetComponent } from './widgets/oee-avg-widget.component';
import { NcOpenWidgetComponent } from './widgets/nc-open-widget.component';
import { NcCriticalWidgetComponent } from './widgets/nc-critical-widget.component';
import { WoOpenWidgetComponent } from './widgets/wo-open-widget.component';
import { MttrWidgetComponent } from './widgets/mttr-widget.component';
import { EquipmentCountWidgetComponent } from './widgets/equipment-count-widget.component';
import { OeeTrendWidgetComponent } from './widgets/oee-trend-widget.component';
import { NcParetoWidgetComponent } from './widgets/nc-pareto-widget.component';

const ALL_WIDGET_TYPES: WidgetType[] = [
  'oee-avg', 'nc-open', 'nc-critical', 'wo-open', 'mttr', 'equipment-count',
  'oee-trend', 'nc-pareto',
];

@Component({
  selector: 'app-kpi-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OeeAvgWidgetComponent,
    NcOpenWidgetComponent,
    NcCriticalWidgetComponent,
    WoOpenWidgetComponent,
    MttrWidgetComponent,
    EquipmentCountWidgetComponent,
    OeeTrendWidgetComponent,
    NcParetoWidgetComponent,
  ],
  templateUrl: './kpi-dashboard.component.html',
  styleUrl: './kpi-dashboard.component.scss',
})
export class KpiDashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);

  readonly widgetConfigs = signal<WidgetConfig[]>([]);
  readonly editMode = signal(false);
  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly loadError = signal(false);

  readonly toast = signal<string | null>(null);
  readonly errorToast = signal<string | null>(null);

  private draggedIndex: number | null = null;

  readonly availableForRole = computed<WidgetType[]>(() => {
    const role = this.authService.role();
    if (role === 'SUPERVISOR' || role === 'ADMIN') {
      return ALL_WIDGET_TYPES;
    }
    return ALL_WIDGET_TYPES.filter((t) => !SUPERVISOR_ONLY_WIDGETS.includes(t));
  });

  readonly catalogWidgets = computed<WidgetType[]>(() => {
    const current = new Set(this.widgetConfigs().map((w) => w.type));
    return this.availableForRole().filter((t) => !current.has(t));
  });

  readonly widgetLabels = WIDGET_LABELS;

  ngOnInit(): void {
    this.dashboardService.getLayout().subscribe({
      next: (res) => {
        this.widgetConfigs.set(JSON.parse(res.widgetsJson) as WidgetConfig[]);
        this.isLoading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.isLoading.set(false);
        this.showError('Não foi possível carregar o layout do dashboard');
      },
    });
  }

  toggleEditMode(): void {
    this.editMode.update((v) => !v);
  }

  removeWidget(index: number): void {
    this.widgetConfigs.update((list) => list.filter((_, i) => i !== index));
  }

  addFromCatalog(type: WidgetType): void {
    const list = this.widgetConfigs();
    const col = (list.length % 3) + 1;
    const row = Math.floor(list.length / 3) + 1;
    const newWidget: WidgetConfig = { id: `w${Date.now()}`, type, column: col, row };
    this.widgetConfigs.update((l) => [...l, newWidget]);
  }

  onDragStart(index: number): void {
    this.draggedIndex = index;
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(targetIndex: number): void {
    if (this.draggedIndex === null || this.draggedIndex === targetIndex) return;

    this.widgetConfigs.update((list) => {
      const updated = [...list];
      const [dragged] = updated.splice(this.draggedIndex!, 1);
      updated.splice(targetIndex, 0, dragged);
      return updated.map((w, i) => ({
        ...w,
        column: (i % 3) + 1,
        row: Math.floor(i / 3) + 1,
      }));
    });
    this.draggedIndex = null;
  }

  saveLayout(): void {
    this.isSaving.set(true);
    const json = JSON.stringify(this.widgetConfigs());
    this.dashboardService.saveLayout(json).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.editMode.set(false);
        this.showToast('Layout salvo com sucesso');
      },
      error: (err: { error?: { message?: string } }) => {
        this.isSaving.set(false);
        this.showError(err?.error?.message ?? 'Erro ao salvar layout');
      },
    });
  }

  confirmReset(): void {
    if (!window.confirm('Resetar para o layout padrão? As personalizações serão perdidas.')) return;

    this.dashboardService.deleteLayout().subscribe({
      next: () => {
        this.dashboardService.getLayout().subscribe({
          next: (res) => {
            this.widgetConfigs.set(JSON.parse(res.widgetsJson) as WidgetConfig[]);
            this.editMode.set(false);
          },
        });
      },
    });
  }

  gridStyle(widget: WidgetConfig): Record<string, string> {
    return {
      'grid-column': String(widget.column),
      'grid-row': String(widget.row),
    };
  }

  private showToast(msg: string): void {
    this.toast.set(msg);
    setTimeout(() => this.toast.set(null), 3000);
  }

  private showError(msg: string): void {
    this.errorToast.set(msg);
    setTimeout(() => this.errorToast.set(null), 5000);
  }
}
