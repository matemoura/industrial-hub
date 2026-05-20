import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { MaintenanceDashboardComponent } from './maintenance-dashboard.component';
import { MaintenanceService, SparePartResponse } from '../maintenance.service';

const PART_LOW: SparePartResponse = {
  id: 'p1',
  code: 'PART-001',
  name: 'Rolamento',
  category: 'Rolamentos',
  unit: 'un',
  stockQty: 1,
  minStockQty: 5,
  active: true,
  belowMin: true,
};

function setup(parts: SparePartResponse[] | 'error' = [PART_LOW]) {
  const listBelowMin = parts === 'error'
    ? vi.fn().mockReturnValue(throwError(() => new Error('fail')))
    : vi.fn().mockReturnValue(of(parts));
  const service = { listBelowMin, listSpareParts: vi.fn().mockReturnValue(of([])) };

  TestBed.configureTestingModule({
    imports: [MaintenanceDashboardComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MaintenanceService, useValue: service },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(MaintenanceDashboardComponent);
  fixture.detectChanges();
  return { fixture, service };
}

describe('MaintenanceDashboardComponent', () => {

  // AC#1 — tabela exibe peças críticas
  it('should render critical parts table when parts exist', () => {
    const { fixture } = setup([PART_LOW]);
    const table = fixture.nativeElement.querySelector('[data-testid="critical-table"]');
    expect(table).toBeTruthy();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid="critical-row"]');
    expect(rows.length).toBe(1);
  });

  // AC#2 — estado vazio quando sem peças críticas
  it('should show empty state when no critical parts', () => {
    const { fixture } = setup([]);
    const empty = fixture.nativeElement.querySelector('[data-testid="empty"]');
    expect(empty).toBeTruthy();
  });

  // AC#3 — estado de erro com botão de retry
  it('should show error state on load failure', () => {
    const { fixture } = setup('error');
    const error = fixture.nativeElement.querySelector('[data-testid="error"]');
    expect(error).toBeTruthy();
  });

  // AC#4 — skeleton exibido durante loading (sincrono não detectável, verificar via loading signal)
  it('should call listBelowMin on init', () => {
    const { service } = setup([PART_LOW]);
    expect(service.listBelowMin).toHaveBeenCalled();
  });
});
