import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { GedListComponent } from './ged-list.component';
import { Page, DocumentSummary } from '../ged.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const MOCK_PAGE: Page<DocumentSummary> = {
  content: [
    {
      id: 'doc-1',
      code: 'SOP-001',
      title: 'Procedimento de Limpeza',
      category: 'SOP',
      status: 'PUBLISHED',
      currentRevisionNumber: '1.0',
      updatedAt: '2026-05-01T10:00:00Z',
    },
  ],
  totalPages: 1,
  totalElements: 1,
  number: 0,
  size: 20,
};

describe('GedListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [GedListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(GedListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  // (a) skeleton durante carregamento (antes da resposta HTTP)
  it('(a) deve exibir skeleton enquanto carrega', () => {
    const fixture = setup();
    const compiled: HTMLElement = fixture.nativeElement;

    // Antes de fazer flush, loading = true → skeleton deve estar visível
    const skeletonRows = compiled.querySelectorAll('.skeleton-row');
    expect(skeletonRows.length).toBe(5);

    // Limpa requisição pendente
    httpTesting.expectOne((req) => req.url.includes('/api/v1/qms/ged')).flush(MOCK_PAGE);
  });

  // (b) botão "Novo Documento" oculto para OPERATOR
  it('(b) deve ocultar botão "Novo Documento" para OPERATOR', () => {
    const fixture = setup('OPERATOR');
    const compiled: HTMLElement = fixture.nativeElement;

    httpTesting.expectOne((req) => req.url.includes('/api/v1/qms/ged')).flush(MOCK_PAGE);
    fixture.detectChanges();

    const createBtn = compiled.querySelector('[data-testid="create-btn"]');
    expect(createBtn).toBeNull();
  });

  // (c) chip PUBLISHED exibe cor verde correta
  it('(c) chip PUBLISHED deve ter background verde (#3FA66A)', () => {
    const fixture = setup('SUPERVISOR');
    const compiled: HTMLElement = fixture.nativeElement;

    httpTesting.expectOne((req) => req.url.includes('/api/v1/qms/ged')).flush(MOCK_PAGE);
    fixture.detectChanges();

    const chip = compiled.querySelector('[data-testid="status-PUBLISHED"]') as HTMLElement | null;
    expect(chip).not.toBeNull();
    // O style.background pode ser retornado em rgb() pelo browser
    const bg = chip!.style.background || chip!.style.backgroundColor;
    // Aceita hex direto ou rgb equivalente de #3FA66A (63, 166, 106)
    const isGreen = bg.includes('#3FA66A') ||
                    bg.includes('#3fa66a') ||
                    bg.includes('rgb(63, 166, 106)');
    expect(isGreen).toBe(true);
  });

  // (d) filtro de status dispara nova requisição com o parâmetro correto
  it('(d) filtro de status deve disparar nova requisição com parâmetro status', () => {
    const fixture = setup('SUPERVISOR');
    const compiled: HTMLElement = fixture.nativeElement;

    // Flush requisição inicial
    httpTesting.expectOne((req) => req.url.includes('/api/v1/qms/ged')).flush(MOCK_PAGE);
    fixture.detectChanges();

    // Alterar select de status
    const statusSelect = compiled.querySelector('#filterStatus') as HTMLSelectElement;
    statusSelect.value = 'PUBLISHED';
    statusSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    // Clicar no botão Filtrar
    const filterBtn = Array.from(compiled.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Filtrar'
    );
    expect(filterBtn).not.toBeNull();
    filterBtn!.click();
    fixture.detectChanges();

    // Verificar que nova requisição tem parâmetro status=PUBLISHED
    const req = httpTesting.expectOne((r) =>
      r.url.includes('/api/v1/qms/ged') && r.params.get('status') === 'PUBLISHED'
    );
    expect(req.request.params.get('status')).toBe('PUBLISHED');
    req.flush({ ...MOCK_PAGE, content: [] });
  });
});
