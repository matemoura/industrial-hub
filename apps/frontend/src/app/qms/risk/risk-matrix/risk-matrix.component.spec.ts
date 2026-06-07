import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RiskMatrixComponent } from './risk-matrix.component';
import { RiskMatrixResponse } from '../../risk.service';

const SAMPLE_MATRIX: RiskMatrixResponse = {
  cells: [
    { severity: 8, occurrence: 6, count: 1, riskLevel: 'CRITICAL' },
    { severity: 3, occurrence: 3, count: 2, riskLevel: 'LOW' },
  ],
};

describe('RiskMatrixComponent', () => {
  let httpTesting: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      imports: [RiskMatrixComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RiskMatrixComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  function flushMatrix(): void {
    httpTesting
      .expectOne((req) => req.url.includes('/matrix'))
      .flush(SAMPLE_MATRIX);
  }

  // (a) célula (8,6) exibe count=1 quando há risco nessa posição
  it('celula_8_6_exibe_count_com_risco', () => {
    const fixture = setup();
    flushMatrix();
    fixture.detectChanges();

    const cell = fixture.nativeElement.querySelector('[data-testid="cell-8-6"]');
    expect(cell).not.toBeNull();
    expect(cell.textContent.trim()).toBe('1');
  });

  // (b) célula vazia exibe sem número
  it('celula_vazia_sem_numero', () => {
    const fixture = setup();
    flushMatrix();
    fixture.detectChanges();

    // Cell (8,7) has no entry in the matrix
    const cell = fixture.nativeElement.querySelector('[data-testid="cell-8-7"]');
    expect(cell).not.toBeNull();
    expect(cell.textContent.trim()).toBe('');
  });
});
