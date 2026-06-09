import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ComplaintDashboardComponent } from './complaint-dashboard.component';
import { AuthService } from '../../../auth/auth.service';
import { ComplaintIndicators } from '../../complaints.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const SAMPLE_INDICATORS: ComplaintIndicators = {
  totalReceived: 20,
  byStatus: {
    RECEIVED: 5,
    UNDER_INVESTIGATION: 8,
    INVESTIGATION_COMPLETED: 3,
    CLOSED: 4,
  },
  bySeverity: { LOW: 5, MEDIUM: 8, HIGH: 5, CRITICAL: 2 },
  reportedToAnvisa: 3,
  avgResolutionDays: 14.2,
  byProduct: [
    { productCode: 'PROD-001', count: 10 },
    { productCode: 'PROD-002', count: 5 },
  ],
  bySource: { CLIENT: 12, DISTRIBUTOR: 4, REGULATORY_BODY: 2, INTERNAL: 2 },
};

describe('ComplaintDashboardComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [ComplaintDashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ComplaintDashboardComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  function flushIndicators(indicators = SAMPLE_INDICATORS): void {
    httpTesting
      .expectOne((req) => req.url.includes('/indicators'))
      .flush(indicators);
  }

  // (a) Cards de indicadores renderizam com dados mockados
  it('kpi_cards_renderizam_com_dados_mockados', () => {
    const fixture = setup();
    flushIndicators();
    fixture.detectChanges();

    const kpiGrid = fixture.nativeElement.querySelector('[data-testid="kpi-grid"]');
    expect(kpiGrid).not.toBeNull();

    const totalCard = fixture.nativeElement.querySelector('[data-testid="kpi-total-received"]');
    expect(totalCard).not.toBeNull();
    expect(totalCard.textContent).toContain('20');
  });

  // (b) Card "Não Reportadas" tem danger quando > 0
  it('card_nao_reportadas_danger_quando_positivo', () => {
    const fixture = setup();
    flushIndicators();
    fixture.detectChanges();

    // totalReceived=20, reportedToAnvisa=3 → notReported=17 > 0
    const dangerCard = fixture.nativeElement.querySelector('.kpi-card--danger');
    expect(dangerCard).not.toBeNull();
  });

  // (c) Top produtos renderiza lista com dados
  it('top_produtos_renderiza_lista', () => {
    const fixture = setup();
    flushIndicators();
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.product-list__item');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('PROD-001');
  });
});
