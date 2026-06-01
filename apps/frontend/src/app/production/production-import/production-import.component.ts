import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { ImportResult, ProductionService } from '../production.service';

type ImportTab = 'products' | 'stock' | 'orders' | 'cycle-times';

interface TabResult {
  result: ImportResult | null;
  error: string | null;
  uploading: boolean;
  file: File | null;
  dragOver: boolean;
}

function emptyTab(): TabResult {
  return { result: null, error: null, uploading: false, file: null, dragOver: false };
}

@Component({
  selector: 'app-production-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './production-import.component.html',
  styleUrl: './production-import.component.scss',
})
export class ProductionImportComponent {
  private readonly service = inject(ProductionService);
  private readonly auth    = inject(AuthService);
  readonly destroyRef      = inject(DestroyRef);

  readonly role    = this.auth.role;
  readonly isAdmin = () => this.role() === 'ADMIN';

  readonly activeTab = signal<ImportTab>('products');

  readonly tabs: { key: ImportTab; label: string; minRole: 'ADMIN' | 'SUPERVISOR' }[] = [
    { key: 'products',    label: 'Produtos',           minRole: 'ADMIN' },
    { key: 'stock',       label: 'Estoque',             minRole: 'SUPERVISOR' },
    { key: 'orders',      label: 'Ordens de Produção',  minRole: 'SUPERVISOR' },
    { key: 'cycle-times', label: 'Tempos de Ciclo',     minRole: 'SUPERVISOR' },
  ];

  readonly state = signal<Record<ImportTab, TabResult>>({
    'products':    emptyTab(),
    'stock':       emptyTab(),
    'orders':      emptyTab(),
    'cycle-times': emptyTab(),
  });

  get visibleTabs() {
    const r = this.role();
    return this.tabs.filter(t =>
      t.minRole === 'SUPERVISOR' || (t.minRole === 'ADMIN' && r === 'ADMIN'),
    );
  }

  selectTab(tab: ImportTab): void {
    this.activeTab.set(tab);
  }

  get currentState(): TabResult {
    return this.state()[this.activeTab()];
  }

  onDragOver(e: DragEvent): void {
    e.preventDefault();
    this.patchTab(this.activeTab(), { dragOver: true });
  }

  onDragLeave(): void {
    this.patchTab(this.activeTab(), { dragOver: false });
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.patchTab(this.activeTab(), { dragOver: false });
    const file = e.dataTransfer?.files[0] ?? null;
    if (file) this.patchTab(this.activeTab(), { file, result: null, error: null });
  }

  onFileChange(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0] ?? null;
    if (file) this.patchTab(this.activeTab(), { file, result: null, error: null });
  }

  clearFile(): void {
    this.patchTab(this.activeTab(), { file: null, result: null, error: null });
  }

  import(): void {
    const tab   = this.activeTab();
    const file  = this.state()[tab].file;
    if (!file) return;

    this.patchTab(tab, { uploading: true, error: null, result: null });

    const obs$ =
      tab === 'products'    ? this.service.importProducts(file)   :
      tab === 'stock'       ? this.service.importStock(file)      :
      tab === 'orders'      ? this.service.importOrders(file)     :
                              this.service.importCycleTimes(file);

    obs$.subscribe({
      next:  r    => this.patchTab(tab, { uploading: false, result: r }),
      error: err  => this.patchTab(tab, { uploading: false, error: err?.error?.message ?? 'Erro ao importar.' }),
    });
  }

  private patchTab(tab: ImportTab, patch: Partial<TabResult>): void {
    this.state.update(s => ({ ...s, [tab]: { ...s[tab], ...patch } }));
  }
}
