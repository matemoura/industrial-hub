import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { CalibrationDashboardComponent } from './calibration-dashboard.component';
import { CalibrationService, CalibrationSummary } from '../../calibration.service';
import { AuthService } from '../../../auth/auth.service';

const MOCK_SUMMARY: CalibrationSummary = {
  totalSchedules: 10,
  overdueCount: 2,
  dueSoon14Days: 3,
  lastMonthRecords: 8,
  outOfToleranceLastMonth: 1,
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

function makeCalibrationService(overrides: Partial<Record<string, ReturnType<typeof vi.fn>>> = {}) {
  return {
    getCalibrationSummary: overrides['getCalibrationSummary'] ?? vi.fn().mockReturnValue(of(MOCK_SUMMARY)),
    listSchedules: overrides['listSchedules'] ?? vi.fn().mockReturnValue(of([])),
    runAlertsNow: overrides['runAlertsNow'] ?? vi.fn().mockReturnValue(of({ alertsSent: 3 })),
    createSchedule: vi.fn(),
    updateSchedule: vi.fn(),
    deactivateSchedule: vi.fn(),
    listRecords: vi.fn(),
    createRecord: vi.fn(),
    getCertificateUrl: vi.fn(),
  };
}

describe('CalibrationDashboardComponent', () => {
  let fixture: ComponentFixture<CalibrationDashboardComponent>;
  let component: CalibrationDashboardComponent;

  function setup(role = 'SUPERVISOR', calibSvcOverrides: Partial<Record<string, ReturnType<typeof vi.fn>>> = {}) {
    TestBed.configureTestingModule({
      imports: [CalibrationDashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CalibrationService, useValue: makeCalibrationService(calibSvcOverrides) },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalibrationDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should load summary on init', () => {
    setup();
    expect(component.summary()).toEqual(MOCK_SUMMARY);
  });

  it('should show run-alerts button for ADMIN', () => {
    setup('ADMIN');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-run-alerts"]');
    expect(btn).toBeTruthy();
  });

  it('should hide run-alerts button for SUPERVISOR', () => {
    setup('SUPERVISOR');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-run-alerts"]');
    expect(btn).toBeFalsy();
  });
});
