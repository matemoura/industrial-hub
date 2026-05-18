import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NcRcaComponent } from './nc-rca.component';
import { NcResponse } from '../../qms.service';

const BASE_NC: NcResponse = {
  id: 'nc-1',
  title: 'Peça fora de tolerância',
  type: 'PRODUCT',
  severity: 'HIGH',
  status: 'IN_ANALYSIS',
  reportedBy: 'op1',
  reportedAt: '2026-05-01T10:00:00',
  description: null,
  closedAt: null,
  closedBy: null,
  supplierId: null,
  supplierName: null,
  actions: [],
  rca: null,
};

function createComponent(nc: NcResponse, role: string | null = 'SUPERVISOR') {
  const fixture = TestBed.createComponent(NcRcaComponent);
  fixture.componentRef.setInput('nc', nc);
  fixture.componentRef.setInput('role', role);
  fixture.detectChanges();
  return fixture;
}

describe('NcRcaComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NcRcaComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const { componentInstance } = createComponent(BASE_NC);
    expect(componentInstance).toBeTruthy();
  });

  describe('AC-2 — botão "Iniciar Análise" para SUPERVISOR sem RCA', () => {
    it('shows initiate button when SUPERVISOR and no rca', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-initiate-rca"]');
      expect(btn).not.toBeNull();
    });

    it('does not show initiate button for OPERATOR', () => {
      const fixture = createComponent(BASE_NC, 'OPERATOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-initiate-rca"]');
      expect(btn).toBeNull();
    });

    it('initiate button is disabled when NC is OPEN', () => {
      const nc = { ...BASE_NC, status: 'OPEN' as const };
      const fixture = createComponent(nc, 'SUPERVISOR');
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-initiate-rca"]');
      expect(btn).not.toBeNull();
      expect(btn.disabled).toBe(true);
    });

    it('initiate button is enabled when NC is IN_ANALYSIS', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-initiate-rca"]');
      expect(btn.disabled).toBe(false);
    });
  });

  describe('AC-3 — wizard inicia com 1 par e botão adicionar próximo', () => {
    it('shows wizard with 1 pair after clicking initiate', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-initiate-rca"]');
      btn.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="rca-pair-1"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="rca-pair-2"]')).toBeNull();
    });

    it('shows add-pair button when why1 and answer1 are filled', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      fixture.componentInstance.showWizard.set(true);
      fixture.componentInstance.why1.set('Por que falhou?');
      fixture.componentInstance.answer1.set('Falta de calibração');
      fixture.detectChanges();

      const addBtn = fixture.nativeElement.querySelector('[data-testid="btn-add-pair"]');
      expect(addBtn).not.toBeNull();
    });

    it('does not show add-pair button when only why1 is filled (answer1 missing)', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      fixture.componentInstance.showWizard.set(true);
      fixture.componentInstance.why1.set('Por que falhou?');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="btn-add-pair"]')).toBeNull();
    });

    it('adds a new pair when add button clicked', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      fixture.componentInstance.showWizard.set(true);
      fixture.componentInstance.why1.set('Por que falhou?');
      fixture.componentInstance.answer1.set('Falta de calibração');
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="btn-add-pair"]').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="rca-pair-2"]')).not.toBeNull();
    });

    it('hides add-pair button when activePairs reaches 5', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const comp = fixture.componentInstance;
      comp.showWizard.set(true);
      comp.activePairs.set(5);
      comp.why1.set('p1'); comp.why2.set('p2'); comp.why3.set('p3'); comp.why4.set('p4'); comp.why5.set('p5');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="btn-add-pair"]')).toBeNull();
    });
  });

  describe('AC-4 — causa raiz aparece após preencher why1 + answer1', () => {
    it('hides root cause field when only why1 is filled', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      fixture.componentInstance.showWizard.set(true);
      fixture.componentInstance.why1.set('Por quê?');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="root-cause"]')).toBeNull();
    });

    it('shows root cause field when why1 and answer1 are filled', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      fixture.componentInstance.showWizard.set(true);
      fixture.componentInstance.why1.set('Por quê?');
      fixture.componentInstance.answer1.set('Resposta');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="root-cause"]')).not.toBeNull();
    });
  });

  describe('AC-5 — salvar envia POST (nova) ou PUT (existente)', () => {
    it('sends POST when no existing rca', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const comp = fixture.componentInstance;
      comp.showWizard.set(true);
      comp.why1.set('Por quê?');
      comp.answer1.set('Resposta');
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]').click();

      const req = httpTesting.expectOne('/api/v1/qms/non-conformances/nc-1/rca');
      expect(req.request.method).toBe('POST');
      req.flush({ id: 'rca-1', ncId: 'nc-1', why1: 'Por quê?', answer1: 'Resposta',
        why2: null, answer2: null, why3: null, answer3: null, why4: null, answer4: null,
        why5: null, answer5: null, rootCause: null, createdBy: 'supervisor1',
        createdAt: '2026-05-15T10:00:00', updatedAt: null });
    });

    it('sends PUT when rca already exists', () => {
      const existingRca = {
        id: 'rca-1', ncId: 'nc-1', why1: 'Antigo', answer1: null,
        why2: null, answer2: null, why3: null, answer3: null,
        why4: null, answer4: null, why5: null, answer5: null,
        rootCause: null, createdBy: 'sup', createdAt: '2026-05-10T10:00:00', updatedAt: null,
      };
      const nc = { ...BASE_NC, rca: existingRca };
      const fixture = createComponent(nc, 'SUPERVISOR');
      const comp = fixture.componentInstance;
      comp.why1.set('Novo porquê');
      comp.answer1.set('Nova resposta');
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]').click();

      const req = httpTesting.expectOne('/api/v1/qms/non-conformances/nc-1/rca');
      expect(req.request.method).toBe('PUT');
      req.flush({ ...existingRca, why1: 'Novo porquê', answer1: 'Nova resposta', updatedAt: '2026-05-15T12:00:00' });
    });

    it('sends PUT on second save after a successful POST in the same session', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const comp = fixture.componentInstance;
      comp.showWizard.set(true);
      comp.why1.set('Por quê?');
      comp.answer1.set('Resposta');
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]').click();

      const createdRca = {
        id: 'rca-new', ncId: 'nc-1', why1: 'Por quê?', answer1: 'Resposta',
        why2: null, answer2: null, why3: null, answer3: null,
        why4: null, answer4: null, why5: null, answer5: null,
        rootCause: null, createdBy: 'supervisor1', createdAt: '2026-05-15T10:00:00', updatedAt: null,
      };
      httpTesting.expectOne('/api/v1/qms/non-conformances/nc-1/rca').flush(createdRca);
      fixture.detectChanges();

      comp.why1.set('Por quê? (editado)');
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]').click();

      const req2 = httpTesting.expectOne('/api/v1/qms/non-conformances/nc-1/rca');
      expect(req2.request.method).toBe('PUT');
      req2.flush({ ...createdRca, why1: 'Por quê? (editado)', updatedAt: '2026-05-15T11:00:00' });
    });
  });

  describe('AC-6 — NC CLOSED: wizard somente leitura', () => {
    it('disables fields when NC is CLOSED and user is SUPERVISOR', () => {
      const nc = { ...BASE_NC, status: 'CLOSED' as const, rca: {
        id: 'rca-1', ncId: 'nc-1', why1: 'Por quê?', answer1: 'Resposta',
        why2: null, answer2: null, why3: null, answer3: null,
        why4: null, answer4: null, why5: null, answer5: null,
        rootCause: null, createdBy: 'sup', createdAt: '2026-05-10T10:00:00', updatedAt: null,
      }};
      const fixture = createComponent(nc, 'SUPERVISOR');
      fixture.detectChanges();

      const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('[data-testid="why-1"]');
      expect(textarea.disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]')).toBeNull();
    });

    it('shows readonly notice when NC is CLOSED', () => {
      const nc = { ...BASE_NC, status: 'CLOSED' as const, rca: {
        id: 'rca-1', ncId: 'nc-1', why1: 'Por quê?', answer1: 'Resposta',
        why2: null, answer2: null, why3: null, answer3: null,
        why4: null, answer4: null, why5: null, answer5: null,
        rootCause: null, createdBy: 'sup', createdAt: '2026-05-10T10:00:00', updatedAt: null,
      }};
      const fixture = createComponent(nc, 'SUPERVISOR');
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('somente leitura');
    });
  });

  describe('AC-7 — OPERATOR: visualização somente leitura', () => {
    it('fields are disabled for OPERATOR even in IN_ANALYSIS', () => {
      const nc = { ...BASE_NC, rca: {
        id: 'rca-1', ncId: 'nc-1', why1: 'Por quê?', answer1: 'Resposta',
        why2: null, answer2: null, why3: null, answer3: null,
        why4: null, answer4: null, why5: null, answer5: null,
        rootCause: null, createdBy: 'sup', createdAt: '2026-05-10T10:00:00', updatedAt: null,
      }};
      const fixture = createComponent(nc, 'OPERATOR');
      fixture.detectChanges();

      const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('[data-testid="why-1"]');
      expect(textarea.disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]')).toBeNull();
    });
  });

  describe('error handling', () => {
    it('shows error message on save failure', () => {
      const fixture = createComponent(BASE_NC, 'SUPERVISOR');
      const comp = fixture.componentInstance;
      comp.showWizard.set(true);
      comp.why1.set('Por quê?');
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="btn-save-rca"]').click();

      httpTesting.expectOne('/api/v1/qms/non-conformances/nc-1/rca').flush(
        { message: 'RCA só pode ser criada após início da análise' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="rca-error"]');
      expect(error).not.toBeNull();
      expect(error.textContent).toContain('início da análise');
    });
  });
});
