import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TrainingRecordListComponent } from './training-record-list.component';
import { TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const future = new Date();
future.setFullYear(future.getFullYear() + 1);

const past = new Date();
past.setFullYear(past.getFullYear() - 1);

const nearFuture = new Date();
nearFuture.setDate(nearFuture.getDate() + 15);

const mockRecords = [
  {
    id: 'r1', courseId: 'c1', courseCode: 'GMP-001', courseTitle: 'GMP Básico',
    username: 'alice', completedAt: '2025-01-01', expiresAt: future.toISOString().slice(0, 10),
    passed: true, hasCertificate: true, recordedBy: 'admin', recordedAt: '2025-01-01T00:00:00',
    effectivenessResult: null,
  },
  {
    id: 'r2', courseId: 'c2', courseCode: 'SAF-001', courseTitle: 'Segurança',
    username: 'bob', completedAt: '2023-01-01', expiresAt: past.toISOString().slice(0, 10),
    passed: true, hasCertificate: false, recordedBy: 'admin', recordedAt: '2023-01-01T00:00:00',
    effectivenessResult: null,
  },
  {
    id: 'r3', courseId: 'c1', courseCode: 'GMP-001', courseTitle: 'GMP Básico',
    username: 'carol', completedAt: '2024-01-01', expiresAt: null,
    passed: false, hasCertificate: false, recordedBy: 'admin', recordedAt: '2024-01-01T00:00:00',
    effectivenessResult: null,
  },
];

const mockPage = { content: mockRecords, totalPages: 1, number: 0, totalElements: 3 };
const mockCourses = { content: [
  { id: 'c1', code: 'GMP-001', title: 'GMP Básico', category: 'GMP', durationHours: 8, active: true, requiredForRoles: [], createdAt: '2024-01-01T00:00:00' },
], totalPages: 1, number: 0, totalElements: 1 };

describe('TrainingRecordListComponent', () => {
  let fixture: ComponentFixture<TrainingRecordListComponent>;
  let component: TrainingRecordListComponent;
  let getRecords: ReturnType<typeof vi.fn>;
  let getCourses: ReturnType<typeof vi.fn>;
  let getCertificateUrl: ReturnType<typeof vi.fn>;
  let authRole: ReturnType<typeof signal<string>>;

  beforeEach(async () => {
    getRecords = vi.fn().mockReturnValue(of(mockPage));
    getCourses = vi.fn().mockReturnValue(of(mockCourses));
    getCertificateUrl = vi.fn();
    authRole = signal('SUPERVISOR');

    const service = {
      getRecords,
      getCourses,
      getCertificateUrl,
      createRecord: vi.fn(),
      deleteRecord: vi.fn(),
      assessEffectiveness: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [TrainingRecordListComponent],
      providers: [
        { provide: TrainingService, useValue: service },
        { provide: AuthService, useValue: { role: authRole } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingRecordListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load records on init', () => {
    expect(getRecords).toHaveBeenCalled();
    expect(component.records().length).toBe(3);
    expect(component.loading()).toBe(false);
  });

  it('should load courses on init', () => {
    expect(getCourses).toHaveBeenCalled();
    expect(component.courses().length).toBe(1);
  });

  it('should classify expired record with expiry--expired class', () => {
    const expiredRecord = mockRecords[1] as any;
    expect(component.expiryClass(expiredRecord)).toBe('expiry--expired');
  });

  it('should classify valid record with no expiry class', () => {
    const validRecord = mockRecords[0] as any;
    expect(component.expiryClass(validRecord)).toBe('');
  });

  it('should classify null expiresAt record with no expiry class', () => {
    const permanentRecord = mockRecords[2] as any;
    expect(component.expiryClass(permanentRecord)).toBe('');
  });

  it('should show new record form toggle for SUPERVISOR', () => {
    expect(component.isSupervisor()).toBe(true);
  });

  it('should not allow new records form for OPERATOR', () => {
    authRole.set('OPERATOR');
    expect(component.isSupervisor()).toBe(false);
  });

  it('should call getCertificateUrl and open window on download', () => {
    getCertificateUrl.mockReturnValue(of({ url: 'https://storage/cert.pdf' }));
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null as any);

    component.downloadCertificate(mockRecords[0] as any);

    expect(getCertificateUrl).toHaveBeenCalledWith('r1');
    expect(openSpy).toHaveBeenCalledWith('https://storage/cert.pdf', '_blank', 'noopener,noreferrer');

    openSpy.mockRestore();
  });

  it('should filter records by username when filterUsername changes', () => {
    component.filterUsername.set('alice');
    component.loadRecords(0);
    expect(getRecords).toHaveBeenCalledWith(
      expect.objectContaining({ username: 'alice' }), 0
    );
  });

  it('should filter records by passed when filterPassed is set to true', () => {
    component.filterPassed.set('true');
    component.loadRecords(0);
    expect(getRecords).toHaveBeenCalledWith(
      expect.objectContaining({ passed: true }), 0
    );
  });

  it('should handle load error gracefully', () => {
    getRecords.mockReturnValue(throwError(() => new Error('err')));
    component.loadRecords(0);
    expect(component.loading()).toBe(false);
  });

  it('should open effectiveness form for a record', () => {
    component.openEffectivenessForm('r1');
    expect(component.showEffectivenessForm()).toBe('r1');
    expect(component.effResult()).toBe('EFFECTIVE');
  });
});
