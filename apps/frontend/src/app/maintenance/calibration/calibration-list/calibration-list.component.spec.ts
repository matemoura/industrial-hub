import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { CalibrationListComponent } from './calibration-list.component';
import { CalibrationService, CalibrationSchedule, CalibrationRecord } from '../../calibration.service';
import { MaintenanceService } from '../../maintenance.service';
import { AuthService } from '../../../auth/auth.service';

function today(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

const SCHEDULE_OVERDUE: CalibrationSchedule = {
  id: 's1', equipmentId: 'e1', equipmentCode: 'EQ-001', equipmentName: 'Balança A',
  intervalDays: 30, nextDueAt: today(-1), overdue: true, active: true,
};
const SCHEDULE_DUE_SOON: CalibrationSchedule = {
  id: 's2', equipmentId: 'e2', equipmentCode: 'EQ-002', equipmentName: 'Medidor B',
  intervalDays: 90, nextDueAt: today(7), overdue: false, active: true,
};
const SCHEDULE_OK: CalibrationSchedule = {
  id: 's3', equipmentId: 'e3', equipmentCode: 'EQ-003', equipmentName: 'Sensor C',
  intervalDays: 180, nextDueAt: today(30), overdue: false, active: true,
};

const RECORD_WITH_NC: CalibrationRecord = {
  id: 'r1', scheduleId: 's1', equipmentCode: 'EQ-001',
  calibratedAt: '2026-05-01', result: 'OUT_OF_TOLERANCE',
  technician: 'João', hasCertificate: false,
  autoNcId: 'nc-uuid-123',
  recordedAt: '2026-05-01T10:00:00',
};

const RECORD_WITH_CERT: CalibrationRecord = {
  id: 'r2', scheduleId: 's1', equipmentCode: 'EQ-001',
  calibratedAt: '2026-04-01', result: 'IN_TOLERANCE',
  technician: 'Maria', hasCertificate: true,
  recordedAt: '2026-04-01T09:00:00',
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

function makeCalibrationService(overrides: Partial<Record<string, ReturnType<typeof vi.fn>>> = {}) {
  return {
    listSchedules: overrides['listSchedules'] ?? vi.fn().mockReturnValue(of([SCHEDULE_OVERDUE, SCHEDULE_DUE_SOON, SCHEDULE_OK])),
    getCalibrationSummary: overrides['getCalibrationSummary'] ?? vi.fn().mockReturnValue(of({
      totalSchedules: 3, overdueCount: 1, dueSoon14Days: 1, lastMonthRecords: 5, outOfToleranceLastMonth: 1,
    })),
    listRecords: overrides['listRecords'] ?? vi.fn().mockReturnValue(of([])),
    createSchedule: overrides['createSchedule'] ?? vi.fn(),
    createRecord: overrides['createRecord'] ?? vi.fn(),
    deactivateSchedule: overrides['deactivateSchedule'] ?? vi.fn(),
    getCertificateUrl: overrides['getCertificateUrl'] ?? vi.fn(),
    runAlertsNow: overrides['runAlertsNow'] ?? vi.fn(),
  };
}

function makeMaintenanceService() {
  return {
    listEquipment: vi.fn().mockReturnValue(of([])),
  };
}

describe('CalibrationListComponent', () => {
  let fixture: ComponentFixture<CalibrationListComponent>;
  let component: CalibrationListComponent;

  function setup(role = 'OPERATOR', calibSvcOverrides: Partial<Record<string, ReturnType<typeof vi.fn>>> = {}) {
    TestBed.configureTestingModule({
      imports: [CalibrationListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CalibrationService, useValue: makeCalibrationService(calibSvcOverrides) },
        { provide: MaintenanceService, useValue: makeMaintenanceService() },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalibrationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should show chip--danger for overdue schedule (nextDueAt = yesterday)', () => {
    setup();
    const cls = component.dueSoonClass(SCHEDULE_OVERDUE);
    expect(cls).toContain('chip--danger');
  });

  it('should show chip--warn for due-soon schedule (nextDueAt = today+7)', () => {
    setup();
    const cls = component.dueSoonClass(SCHEDULE_DUE_SOON);
    expect(cls).toContain('chip--warn');
  });

  it('should show chip--ok for valid schedule (nextDueAt = today+30)', () => {
    setup();
    const cls = component.dueSoonClass(SCHEDULE_OK);
    expect(cls).toContain('chip--ok');
  });

  it('should show NC link when autoNcId is present', () => {
    const listRecords = vi.fn().mockReturnValue(of([RECORD_WITH_NC]));
    setup('OPERATOR', { listRecords });

    component.selectedScheduleId.set('s1');
    component.records.set([RECORD_WITH_NC]);
    fixture.detectChanges();

    const ncLink = fixture.nativeElement.querySelector('[data-testid="nc-link"]');
    expect(ncLink).toBeTruthy();
  });

  it('should hide "Novo Plano" button for OPERATOR', () => {
    setup('OPERATOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-plan"]');
    expect(btn).toBeFalsy();
  });

  it('should show "Novo Plano" button for SUPERVISOR', () => {
    setup('SUPERVISOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-plan"]');
    expect(btn).toBeTruthy();
  });

  it('should show warning message when result is OUT_OF_TOLERANCE', () => {
    setup('SUPERVISOR');
    component.showRecordForm.set(true);
    component.recordResult.set('OUT_OF_TOLERANCE');
    fixture.detectChanges();

    const warning = fixture.nativeElement.querySelector('[data-testid="oot-warning"]');
    expect(warning).toBeTruthy();
  });

  it('should call getCertificateUrl and open window on download', () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const getCertificateUrl = vi.fn().mockReturnValue(of({ url: 'https://example.com/cert.pdf' }));
    setup('OPERATOR', { getCertificateUrl });

    component.downloadCertificate(RECORD_WITH_CERT);

    expect(getCertificateUrl).toHaveBeenCalledWith(RECORD_WITH_CERT.id);
    expect(openSpy).toHaveBeenCalledWith('https://example.com/cert.pdf', '_blank', 'noopener,noreferrer');
    openSpy.mockRestore();
  });

  it('should load records when schedule is selected', () => {
    const listRecords = vi.fn().mockReturnValue(of([RECORD_WITH_NC, RECORD_WITH_CERT]));
    setup('OPERATOR', { listRecords });

    component.selectSchedule(SCHEDULE_OVERDUE);

    expect(listRecords).toHaveBeenCalledWith(SCHEDULE_OVERDUE.id);
    expect(component.selectedScheduleId()).toBe(SCHEDULE_OVERDUE.id);
  });
});
