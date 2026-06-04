import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { QualityReportComponent } from './quality-report.component';
import { QmsService } from '../qms.service';

describe('QualityReportComponent', () => {
  let fixture: ComponentFixture<QualityReportComponent>;
  let component: QualityReportComponent;
  let qmsService: { generateQualityReport: ReturnType<typeof vi.fn> };

  function setup() {
    qmsService = {
      generateQualityReport: vi.fn().mockReturnValue(of(new Blob(['data'], { type: 'application/pdf' }))),
    };

    TestBed.configureTestingModule({
      imports: [QualityReportComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QmsService, useValue: qmsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QualityReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // US-117-AC1: validation error when no dates
  it('should show validation error when from/to are empty', () => {
    setup();
    expect(component.validationError()).toBe('Período obrigatório.');
  });

  // US-117-AC2: validation error when period > 366 days
  it('should reject periods exceeding 366 days', () => {
    setup();
    component.from.set('2025-01-01');
    component.to.set('2026-12-31'); // ~730 days
    expect(component.validationError()).toContain('366 dias');
  });

  // US-117-AC3: validation error when to < from
  it('should reject when to date is before from date', () => {
    setup();
    component.from.set('2025-06-01');
    component.to.set('2025-05-01');
    expect(component.validationError()).toContain('após a data inicial');
  });

  // US-117-AC4: validation error when no sections selected
  it('should require at least one section', () => {
    setup();
    component.from.set('2025-01-01');
    component.to.set('2025-03-31');
    // Uncheck all sections
    component.sections.update(list => list.map(s => ({ ...s, checked: false })));
    expect(component.validationError()).toContain('ao menos uma seção');
  });

  // US-117-AC5: valid form has no errors
  it('should have no validation error with valid dates and sections', () => {
    setup();
    component.from.set('2025-01-01');
    component.to.set('2025-06-30');
    expect(component.validationError()).toBeNull();
  });

  // US-117-AC6: generate calls service with correct payload
  it('should call generateQualityReport with correct payload', () => {
    setup();
    component.from.set('2025-01-01');
    component.to.set('2025-06-30');
    component.format.set('PDF');
    component.generate();
    expect(qmsService.generateQualityReport.mock.calls.length).toBe(1);
    const call = qmsService.generateQualityReport.mock.calls[0][0];
    expect(call.from).toBe('2025-01-01');
    expect(call.to).toBe('2025-06-30');
    expect(call.format).toBe('PDF');
    expect(Array.isArray(call.sections)).toBe(true);
    expect(call.sections.length).toBeGreaterThan(0);
  });

  // US-117-AC7: error displayed when service fails
  it('should show error when generateQualityReport fails', () => {
    qmsService = {
      generateQualityReport: vi.fn().mockReturnValue(
        throwError(() => ({ error: { message: 'Geração falhou' } }))
      ),
    };
    TestBed.configureTestingModule({
      imports: [QualityReportComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QmsService, useValue: qmsService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(QualityReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.from.set('2025-01-01');
    component.to.set('2025-06-30');
    component.generate();
    fixture.detectChanges();
    expect(component.error()).toBe('Geração falhou');
  });

  // US-117-AC8: section toggle works
  it('should toggle section checked state', () => {
    setup();
    const rcaSection = component.sections().find(s => s.id === 'RCA');
    expect(rcaSection?.checked).toBe(false);
    component.toggleSection('RCA');
    const updated = component.sections().find(s => s.id === 'RCA');
    expect(updated?.checked).toBe(true);
  });
});
