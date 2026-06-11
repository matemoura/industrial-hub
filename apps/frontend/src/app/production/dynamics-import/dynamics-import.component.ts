import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { map } from 'rxjs/operators';
import { BomImportResponse, ImportErrorItem, ImportPermissions, ImportResult, ProductionService } from '../production.service';
import { OeeService } from '../../oee/oee.service';

export type ImportKey = 'products' | 'bom' | 'cycleTimes' | 'stock' | 'orders' | 'oeeData';

interface SectionState {
  file: File | null;
  uploading: boolean;
  result: ImportResult | null;
  error: string | null;
  dragOver: boolean;
}

function empty(): SectionState {
  return { file: null, uploading: false, result: null, error: null, dragOver: false };
}

export interface ImportSection {
  key: ImportKey;
  title: string;
  kicker: string;
  description: string;
  permKey: keyof ImportPermissions;
  accent: string;
}

const SECTIONS: ImportSection[] = [
  {
    key: 'products',
    title: 'Catálogo de Produtos',
    kicker: 'PRODUCTS',
    description: 'Importa famílias e produtos (código, nome, tipo, unidade). Substitui os registros existentes pelo código.',
    permKey: 'canImportProducts',
    accent: '#56A4BB',
  },
  {
    key: 'bom',
    title: 'Lista de Materiais (BOM)',
    kicker: 'BOM',
    description: 'Importa a estrutura de materiais por produto. Substitui integralmente a BOM do produto informado.',
    permKey: 'canImportBom',
    accent: '#5F88A1',
  },
  {
    key: 'cycleTimes',
    title: 'Tempos de Ciclo',
    kicker: 'CYCLE TIMES',
    description: 'Importa tempo de ciclo e lead time por produto e processo. Usado no cálculo de capacidade do MRP.',
    permKey: 'canImportCycleTimes',
    accent: '#E8A93C',
  },
  {
    key: 'stock',
    title: 'Estoque (Snapshot)',
    kicker: 'STOCK',
    description: 'Importa a posição de estoque atual por produto. Cria um snapshot com data de referência.',
    permKey: 'canImportStock',
    accent: '#3FA66A',
  },
  {
    key: 'orders',
    title: 'Ordens de Produção',
    kicker: 'ORDERS',
    description: 'Importa ordens abertas e em andamento do Dynamics. Atualiza status, quantidade e datas.',
    permKey: 'canImportOrders',
    accent: '#9CE5EE',
  },
  {
    key: 'oeeData',
    title: 'Dados de Eficiência (OEE)',
    kicker: 'OEE DATA',
    description: 'Importa registros de eficiência, disponibilidade e contagem de trabalhadores por período. Usado no cálculo do OEE.',
    permKey: 'canImportOeeData',
    accent: '#818286',
  },
];

@Component({
  selector: 'app-dynamics-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './dynamics-import.component.html',
  styleUrl: './dynamics-import.component.scss',
})
export class DynamicsImportComponent implements OnInit {
  private readonly service    = inject(ProductionService);
  private readonly oeeService = inject(OeeService);

  readonly sections = SECTIONS;

  readonly permissions = signal<ImportPermissions | null>(null);
  readonly permLoading = signal(true);
  readonly permError   = signal<string | null>(null);

  readonly state = signal<Record<ImportKey, SectionState>>({
    products:   empty(),
    bom:        empty(),
    cycleTimes: empty(),
    stock:      empty(),
    orders:     empty(),
    oeeData:    empty(),
  });

  ngOnInit(): void {
    this.service.getImportPermissions().subscribe({
      next:  p  => { this.permissions.set(p); this.permLoading.set(false); },
      error: () => { this.permError.set('Erro ao carregar permissões.'); this.permLoading.set(false); },
    });
  }

  canImport(section: ImportSection): boolean {
    const p = this.permissions();
    return p ? p[section.permKey] : false;
  }

  onDragOver(e: DragEvent, key: ImportKey): void {
    e.preventDefault();
    this.patch(key, { dragOver: true });
  }

  onDragLeave(key: ImportKey): void {
    this.patch(key, { dragOver: false });
  }

  onDrop(e: DragEvent, key: ImportKey): void {
    e.preventDefault();
    this.patch(key, { dragOver: false });
    const file = e.dataTransfer?.files[0] ?? null;
    if (file) this.patch(key, { file, result: null, error: null });
  }

  onFileChange(e: Event, key: ImportKey): void {
    const file = (e.target as HTMLInputElement).files?.[0] ?? null;
    if (file) this.patch(key, { file, result: null, error: null });
    (e.target as HTMLInputElement).value = '';
  }

  clearFile(key: ImportKey): void {
    this.patch(key, { file: null, result: null, error: null });
  }

  upload(key: ImportKey): void {
    const file = this.state()[key].file;
    if (!file) return;
    this.patch(key, { uploading: true, error: null, result: null });

    const obs$ =
      key === 'products'   ? this.service.importProducts(file)   :
      key === 'bom'        ? this.service.importBom(file).pipe(
                               map((r: BomImportResponse): ImportResult => ({
                                 imported: r.created,
                                 updated:  r.updated,
                                 skipped:  r.totalRecords - r.created - r.updated - r.errors,
                                 errors:   r.errorDetails.map((e): ImportErrorItem => ({ line: e.line, message: e.message })),
                               }))
                             ) :
      key === 'cycleTimes' ? this.service.importCycleTimes(file) :
      key === 'stock'      ? this.service.importStock(file)      :
      key === 'oeeData'    ? this.oeeService.importFile(file, true).pipe(
                               map(r => ({
                                 imported: r.recordsImported,
                                 updated:  0,
                                 skipped:  0,
                                 errors:   [],
                               } as ImportResult))
                             ) :
                             this.service.importOrders(file);

    obs$.subscribe({
      next:  r   => this.patch(key, { uploading: false, result: r }),
      error: err => this.patch(key, { uploading: false, error: err?.error?.message ?? 'Erro ao importar arquivo.' }),
    });
  }

  sectionState(key: ImportKey): SectionState {
    return this.state()[key];
  }

  private patch(key: ImportKey, patch: Partial<SectionState>): void {
    this.state.update(s => ({ ...s, [key]: { ...s[key], ...patch } }));
  }
}
