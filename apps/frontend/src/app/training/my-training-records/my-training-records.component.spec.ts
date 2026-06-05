import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MyTrainingRecordsComponent } from './my-training-records.component';
import { TrainingService } from '../training.service';

const future = new Date();
future.setFullYear(future.getFullYear() + 1);
const past = new Date('2020-01-01');
const nearFuture = new Date();
nearFuture.setDate(nearFuture.getDate() + 15);

const mockRecords = [
  { id: 'r1', courseId: 'c1', courseCode: 'GMP-001', courseTitle: 'GMP Básico', username: 'me', completedAt: '2024-01-01', expiresAt: future.toISOString().slice(0, 10), passed: true, hasCertificate: true, recordedBy: 'admin', recordedAt: '2024-01-01T00:00:00' },
  { id: 'r2', courseId: 'c2', courseCode: 'SAF-001', courseTitle: 'Segurança', username: 'me', completedAt: '2022-01-01', expiresAt: past.toISOString().slice(0, 10), passed: true, hasCertificate: false, recordedBy: 'admin', recordedAt: '2022-01-01T00:00:00' },
  { id: 'r3', courseId: 'c3', courseCode: 'QA-001', courseTitle: 'Qualidade', username: 'me', completedAt: '2024-06-01', expiresAt: nearFuture.toISOString().slice(0, 10), passed: true, hasCertificate: false, recordedBy: 'admin', recordedAt: '2024-06-01T00:00:00' },
];

describe('MyTrainingRecordsComponent', () => {
  let fixture: ComponentFixture<MyTrainingRecordsComponent>;
  let component: MyTrainingRecordsComponent;
  let getMyRecords: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getMyRecords = vi.fn().mockReturnValue(of(mockRecords));

    await TestBed.configureTestingModule({
      imports: [MyTrainingRecordsComponent],
      providers: [{ provide: TrainingService, useValue: { getMyRecords, getCertificateUrl: vi.fn() } }],
    }).compileComponents();

    fixture = TestBed.createComponent(MyTrainingRecordsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load my records on init', () => {
    expect(getMyRecords).toHaveBeenCalled();
    expect(component.records().length).toBe(3);
  });

  it('should classify EXPIRED record correctly', () => {
    const status = component.statusOf(mockRecords[1] as any);
    expect(status).toBe('expired');
  });

  it('should classify EXPIRING record correctly', () => {
    const status = component.statusOf(mockRecords[2] as any);
    expect(status).toBe('expiring');
  });

  it('should classify VALID record correctly', () => {
    const status = component.statusOf(mockRecords[0] as any);
    expect(status).toBe('valid');
  });

  it('should handle load error gracefully', () => {
    getMyRecords.mockReturnValue(throwError(() => new Error('err')));
    component.ngOnInit();
    expect(component.loading()).toBe(false);
  });
});
