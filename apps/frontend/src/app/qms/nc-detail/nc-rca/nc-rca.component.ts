import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreateRcaPayload, NcResponse, QmsService, RcaResponse } from '../../qms.service';

@Component({
  selector: 'app-nc-rca',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './nc-rca.component.html',
  styleUrl: './nc-rca.component.scss',
})
export class NcRcaComponent implements OnInit {
  private readonly qmsService = inject(QmsService);

  readonly nc = input.required<NcResponse>();
  readonly role = input<string | null>(null);

  readonly activePairs = signal(1);
  readonly saving = signal(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly why1 = signal('');
  readonly answer1 = signal('');
  readonly why2 = signal('');
  readonly answer2 = signal('');
  readonly why3 = signal('');
  readonly answer3 = signal('');
  readonly why4 = signal('');
  readonly answer4 = signal('');
  readonly why5 = signal('');
  readonly answer5 = signal('');
  readonly rootCause = signal('');

  readonly showWizard = signal(false);

  readonly savedRca = signal<RcaResponse | null>(null);

  readonly isReadonly = computed(() => {
    const r = this.role();
    if (r !== 'SUPERVISOR' && r !== 'ADMIN') return true;
    return this.nc().status === 'CLOSED';
  });

  readonly canInitiate = computed(() => {
    const r = this.role();
    return (r === 'SUPERVISOR' || r === 'ADMIN') && !this.nc().rca && !this.savedRca();
  });

  readonly initiateDisabled = computed(() => this.nc().status === 'OPEN');

  readonly showRootCauseField = computed(
    () => this.why1().trim().length > 0 && this.answer1().trim().length > 0,
  );

  readonly canAddPair = computed(() => {
    const n = this.activePairs();
    if (n >= 5) return false;
    return (
      this.getNthWhy(n).trim().length > 0 &&
      this.getNthAnswer(n).trim().length > 0
    );
  });

  readonly canSave = computed(() => this.why1().trim().length > 0 && !this.saving());

  readonly pairs = computed(() =>
    Array.from({ length: this.activePairs() }, (_, i) => i + 1),
  );

  ngOnInit(): void {
    const rca = this.nc().rca;
    if (rca) {
      this.savedRca.set(rca);
      this.loadFromRca(rca);
      this.showWizard.set(true);
      this.recalculateActivePairs(rca);
    }
  }

  private loadFromRca(rca: RcaResponse): void {
    this.why1.set(rca.why1 ?? '');
    this.answer1.set(rca.answer1 ?? '');
    this.why2.set(rca.why2 ?? '');
    this.answer2.set(rca.answer2 ?? '');
    this.why3.set(rca.why3 ?? '');
    this.answer3.set(rca.answer3 ?? '');
    this.why4.set(rca.why4 ?? '');
    this.answer4.set(rca.answer4 ?? '');
    this.why5.set(rca.why5 ?? '');
    this.answer5.set(rca.answer5 ?? '');
    this.rootCause.set(rca.rootCause ?? '');
  }

  private recalculateActivePairs(rca: RcaResponse): void {
    let last = 1;
    if (rca.why2) last = 2;
    if (rca.why3) last = 3;
    if (rca.why4) last = 4;
    if (rca.why5) last = 5;
    this.activePairs.set(last);
  }

  initiate(): void {
    if (this.initiateDisabled()) return;
    this.showWizard.set(true);
  }

  addPair(): void {
    if (this.canAddPair()) {
      this.activePairs.update((n) => n + 1);
    }
  }

  save(): void {
    if (!this.canSave() || this.isReadonly()) return;
    this.saving.set(true);
    this.errorMsg.set(null);
    this.successMsg.set(null);

    const payload = this.buildPayload();
    const ncId = this.nc().id;
    const isUpdate = (this.nc().rca ?? this.savedRca()) !== null;
    const request$ = isUpdate
      ? this.qmsService.updateRca(ncId, payload)
      : this.qmsService.createRca(ncId, payload);

    request$.subscribe({
      next: (rca) => {
        this.savedRca.set(rca);
        this.loadFromRca(rca);
        this.recalculateActivePairs(rca);
        this.saving.set(false);
        this.successMsg.set('Análise de causa raiz salva com sucesso.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao salvar RCA. Tente novamente.');
        this.saving.set(false);
      },
    });
  }

  getNthWhy(n: number): string {
    const map: Record<number, () => string> = {
      1: () => this.why1(),
      2: () => this.why2(),
      3: () => this.why3(),
      4: () => this.why4(),
      5: () => this.why5(),
    };
    return map[n]?.() ?? '';
  }

  setNthWhy(n: number, v: string): void {
    const map: Record<number, (val: string) => void> = {
      1: (val) => this.why1.set(val),
      2: (val) => this.why2.set(val),
      3: (val) => this.why3.set(val),
      4: (val) => this.why4.set(val),
      5: (val) => this.why5.set(val),
    };
    map[n]?.(v);
  }

  getNthAnswer(n: number): string {
    const map: Record<number, () => string> = {
      1: () => this.answer1(),
      2: () => this.answer2(),
      3: () => this.answer3(),
      4: () => this.answer4(),
      5: () => this.answer5(),
    };
    return map[n]?.() ?? '';
  }

  setNthAnswer(n: number, v: string): void {
    const map: Record<number, (val: string) => void> = {
      1: (val) => this.answer1.set(val),
      2: (val) => this.answer2.set(val),
      3: (val) => this.answer3.set(val),
      4: (val) => this.answer4.set(val),
      5: (val) => this.answer5.set(val),
    };
    map[n]?.(v);
  }

  private buildPayload(): CreateRcaPayload {
    const payload: CreateRcaPayload = { why1: this.why1().trim() };
    if (this.answer1().trim()) payload.answer1 = this.answer1().trim();
    if (this.why2().trim()) payload.why2 = this.why2().trim();
    if (this.answer2().trim()) payload.answer2 = this.answer2().trim();
    if (this.why3().trim()) payload.why3 = this.why3().trim();
    if (this.answer3().trim()) payload.answer3 = this.answer3().trim();
    if (this.why4().trim()) payload.why4 = this.why4().trim();
    if (this.answer4().trim()) payload.answer4 = this.answer4().trim();
    if (this.why5().trim()) payload.why5 = this.why5().trim();
    if (this.answer5().trim()) payload.answer5 = this.answer5().trim();
    if (this.rootCause().trim()) payload.rootCause = this.rootCause().trim();
    return payload;
  }
}
