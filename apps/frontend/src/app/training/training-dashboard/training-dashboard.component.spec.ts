import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrainingDashboardComponent } from './training-dashboard.component';
import { TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockSummary = {
  totalUsers: 20,
  totalRequiredCompetencies: 60,
  valid: 40,
  expiring: 8,
  expired: 5,
  missing: 7,
  compliancePercent: 66.67,
};

describe('TrainingDashboardComponent', () => {
  let fixture: ComponentFixture<TrainingDashboardComponent>;
  let component: TrainingDashboardComponent;
  let getComplianceSummary: ReturnType<typeof vi.fn>;
  let runAlertsNow: ReturnType<typeof vi.fn>;
  let authRole: ReturnType<typeof signal<string>>;

  beforeEach(async () => {
    getComplianceSummary = vi.fn().mockReturnValue(of(mockSummary));
    runAlertsNow = vi.fn();
    authRole = signal('SUPERVISOR');

    const service = { getComplianceSummary, runAlertsNow };

    await TestBed.configureTestingModule({
      imports: [TrainingDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: TrainingService, useValue: service },
        { provide: AuthService, useValue: { role: authRole } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load compliance summary on init', () => {
    expect(getComplianceSummary).toHaveBeenCalled();
    expect(component.summary()).toEqual(mockSummary);
    expect(component.loading()).toBe(false);
  });

  it('should compute gauge percent correctly', () => {
    expect(component.gaugePercent()).toBe(67);
  });

  it('should return warning color for 60-79%', () => {
    expect(component.gaugeColor()).toBe('#E8A93C');
  });

  it('should return green color for >= 80%', () => {
    component.summary.set({ ...mockSummary, compliancePercent: 85 });
    expect(component.gaugeColor()).toBe('#3FA66A');
  });

  it('should return red color for < 60%', () => {
    component.summary.set({ ...mockSummary, compliancePercent: 45 });
    expect(component.gaugeColor()).toBe('#D24A4A');
  });

  it('should not show run-alerts button for non-admin', () => {
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.btn--secondary');
    expect(btn).toBeNull();
  });

  it('should show run-alerts button for admin', () => {
    authRole.set('ADMIN');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.btn--secondary');
    expect(btn).toBeTruthy();
  });

  it('should call runAlertsNow and show toast on success', () => {
    runAlertsNow.mockReturnValue(of({ alerted: 3 }));
    authRole.set('ADMIN');
    fixture.detectChanges();

    component.runAlerts();

    expect(runAlertsNow).toHaveBeenCalled();
    expect(component.alertToast()).toContain('3');
    expect(component.runningAlerts()).toBe(false);
  });

  it('should show error toast when runAlertsNow fails', () => {
    runAlertsNow.mockReturnValue(throwError(() => new Error('fail')));
    authRole.set('ADMIN');
    fixture.detectChanges();

    component.runAlerts();

    expect(component.alertToast()).toContain('Erro');
    expect(component.runningAlerts()).toBe(false);
  });

  it('should handle load error gracefully', () => {
    getComplianceSummary.mockReturnValue(throwError(() => new Error('err')));
    component.load();
    expect(component.loading()).toBe(false);
  });
});
