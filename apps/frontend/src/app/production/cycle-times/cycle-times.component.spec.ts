import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { CycleTimesComponent } from './cycle-times.component';
import { ProductionService } from '../production.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockProduct = {
  id: 'p1',
  dynamicsCode: 'P001',
  name: 'Produto Alpha',
  description: '',
  family: null as never,
  active: true,
};

const mockCycleTimes = [
  {
    id: 'ct1',
    product: mockProduct,
    cycleSecs: 3661,
    leadTimeDays: 5,
    effectiveDate: '2026-01-01',
  },
  {
    id: 'ct2',
    product: mockProduct,
    cycleSecs: 90,
    leadTimeDays: 3,
    effectiveDate: '2025-06-01',
  },
];

function makeService() {
  return {
    listCycleTimes: vi.fn().mockReturnValue(of(mockCycleTimes)),
    listProducts: vi.fn().mockReturnValue(of({ content: [mockProduct], totalElements: 1, totalPages: 1, number: 0 })),
    importCycleTimes: vi.fn(),
  };
}

function makeAuth(role = 'SUPERVISOR') {
  return { role: signal(role) };
}

async function createComponent(role = 'SUPERVISOR') {
  const svc = makeService();
  const auth = makeAuth(role);
  await TestBed.configureTestingModule({
    imports: [CycleTimesComponent],
    providers: [
      { provide: ProductionService, useValue: svc },
      { provide: AuthService, useValue: auth },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(CycleTimesComponent);
  fixture.detectChanges();
  return { fixture, svc, auth };
}

describe('CycleTimesComponent', () => {
  it('renders table with mocked cycle times', async () => {
    const { fixture } = await createComponent();
    await fixture.whenStable();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="cycle-time-row"]');
    expect(rows.length).toBe(2);

    const firstProduct = fixture.nativeElement.querySelector('[data-testid="ct-product"]');
    expect(firstProduct.textContent.trim()).toBe('Produto Alpha');
  });

  it('formats cycle time correctly', async () => {
    const { fixture } = await createComponent();
    await fixture.whenStable();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    expect(comp.formatCycleTime(3661)).toBe('1h 1m 1s');
    expect(comp.formatCycleTime(90)).toBe('1m 30s');
    expect(comp.formatCycleTime(45)).toBe('45s');
    expect(comp.formatCycleTime(3600)).toBe('1h');
  });

  it('shows import button for SUPERVISOR', async () => {
    const { fixture } = await createComponent('SUPERVISOR');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-cycle-times"]');
    expect(btn).not.toBeNull();
  });

  it('hides import button for OPERATOR', async () => {
    const { fixture } = await createComponent('OPERATOR');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-cycle-times"]');
    expect(btn).toBeNull();
  });

  it('calls importCycleTimes and shows result on success', async () => {
    const mockResult = { imported: 4, updated: 1, skipped: 0, errors: [] };
    const { fixture, svc } = await createComponent('ADMIN');
    svc.importCycleTimes.mockReturnValue(of(mockResult));
    await fixture.whenStable();
    fixture.detectChanges();

    const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-cycle-times"]');
    importBtn.click();
    fixture.detectChanges();

    fixture.componentInstance.selectedFile.set(new File(['data'], 'cycle.xlsx'));
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-upload"]');
    confirmBtn.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(svc.importCycleTimes).toHaveBeenCalled();
    const result = fixture.nativeElement.querySelector('[data-testid="import-result"]');
    expect(result).not.toBeNull();
    const importedCard = fixture.nativeElement.querySelector('[data-testid="result-imported"]');
    expect(importedCard.textContent).toContain('4');
  });

  it('shows upload error when import fails', async () => {
    const { fixture, svc } = await createComponent('ADMIN');
    svc.importCycleTimes.mockReturnValue(throwError(() => ({ error: { message: 'Arquivo corrompido' } })));
    await fixture.whenStable();
    fixture.detectChanges();

    const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-cycle-times"]');
    importBtn.click();
    fixture.detectChanges();

    fixture.componentInstance.selectedFile.set(new File(['data'], 'cycle.xlsx'));
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-upload"]');
    confirmBtn.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const errEl = fixture.nativeElement.querySelector('[data-testid="upload-error"]');
    expect(errEl).not.toBeNull();
    expect(errEl.textContent).toContain('Arquivo corrompido');
  });

  it('shows empty state when no cycle times', async () => {
    const svc = makeService();
    svc.listCycleTimes.mockReturnValue(of([]));
    const auth = makeAuth('OPERATOR');
    await TestBed.configureTestingModule({
      imports: [CycleTimesComponent],
      providers: [
        { provide: ProductionService, useValue: svc },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CycleTimesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).not.toBeNull();
  });
});
