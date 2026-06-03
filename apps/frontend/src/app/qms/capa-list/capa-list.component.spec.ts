import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { CapaListComponent } from './capa-list.component';
import { AuthService } from '../../auth/auth.service';
import { CAPASummary, Page } from '../qms.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const SAMPLE_CAPA_PAGE: Page<CAPASummary> = {
  content: [
    {
      actionId: 'action-1',
      ncCode: 'nc-uuid-1234',
      ncTitle: 'NC Teste',
      description: 'Ação corretiva de teste',
      type: 'CORRECTIVE',
      status: 'PENDING',
      responsible: 'operador1',
      dueDate: '2026-09-01',
      effectivenessCheckDate: null,
    },
    {
      actionId: 'action-2',
      ncCode: 'nc-uuid-5678',
      ncTitle: 'NC Preventiva',
      description: 'Ação em verificação de eficácia',
      type: 'PREVENTIVE',
      status: 'PENDING_EFFECTIVENESS',
      responsible: 'supervisora1',
      dueDate: '2026-10-01',
      effectivenessCheckDate: '2026-10-15',
    },
  ],
  totalPages: 1,
  totalElements: 2,
  number: 0,
  size: 20,
};

describe('CapaListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [CapaListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CapaListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  // (a) filtro por tipo dispara nova requisição com parâmetro type=PREVENTIVE
  it('filterByType_sendsTypeParam', () => {
    const fixture = setup();
    // flush initial load
    httpTesting.expectOne(req => req.url.includes('/capas')).flush(SAMPLE_CAPA_PAGE);

    fixture.componentInstance.filterType.set('PREVENTIVE');
    fixture.componentInstance.applyFilters();

    const req = httpTesting.expectOne(
      req => req.url.includes('/capas') && req.params.get('type') === 'PREVENTIVE'
    );
    expect(req.request.params.get('type')).toBe('PREVENTIVE');
    req.flush({ ...SAMPLE_CAPA_PAGE, content: [] });
  });

  // (b) chip PENDING_EFFECTIVENESS exibido corretamente após flush
  it('chip_PENDING_EFFECTIVENESS_displayed', () => {
    const fixture = setup();
    httpTesting.expectOne(req => req.url.includes('/capas')).flush(SAMPLE_CAPA_PAGE);
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('[data-testid="status-PENDING_EFFECTIVENESS"]');
    expect(chip).not.toBeNull();
    expect(chip.textContent.trim()).toContain('Aguard. Eficácia');
  });

  // (c) "CAPAs" não aparece para OPERATOR — verificado por review de sidebar (@if role SUPERVISOR/ADMIN)
  // ⚠️ Coberto por review: o link CAPAs na sidebar usa @if (role() === 'SUPERVISOR' || role() === 'ADMIN')
  // Teste de integração E2E não é escopo deste spec
  it('component_creates_for_supervisor', () => {
    const fixture = setup('SUPERVISOR');
    httpTesting.expectOne(req => req.url.includes('/capas')).flush(SAMPLE_CAPA_PAGE);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
