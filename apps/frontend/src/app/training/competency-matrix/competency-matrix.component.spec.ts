import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CompetencyMatrixComponent } from './competency-matrix.component';
import { TrainingService } from '../training.service';

const mockRows = [
  { username: 'alice', role: 'OPERATOR', courseId: 'c1', courseCode: 'GMP-001', courseTitle: 'GMP Básico', status: 'VALID' as const, completedAt: '2025-01-10', expiresAt: '2026-01-10' },
  { username: 'bob',   role: 'SUPERVISOR', courseId: 'c2', courseCode: 'QA-001', courseTitle: 'Qualidade', status: 'EXPIRED' as const, completedAt: '2024-01-01', expiresAt: '2025-01-01' },
  { username: 'alice', role: 'OPERATOR', courseId: 'c3', courseCode: 'SAF-001', courseTitle: 'Segurança', status: 'MISSING' as const },
];

describe('CompetencyMatrixComponent', () => {
  let fixture: ComponentFixture<CompetencyMatrixComponent>;
  let component: CompetencyMatrixComponent;
  let getCompetencyMatrix: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getCompetencyMatrix = vi.fn().mockReturnValue(of(mockRows));

    await TestBed.configureTestingModule({
      imports: [CompetencyMatrixComponent],
      providers: [{ provide: TrainingService, useValue: { getCompetencyMatrix } }],
    }).compileComponents();

    fixture = TestBed.createComponent(CompetencyMatrixComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load matrix rows on init', () => {
    expect(component.rows().length).toBe(3);
    expect(component.loading()).toBe(false);
  });

  it('should derive unique roles', () => {
    expect(component.roles()).toEqual(['OPERATOR', 'SUPERVISOR']);
  });

  it('should filter by role', () => {
    component.filterRole.set('OPERATOR');
    expect(component.filtered().length).toBe(2);
  });

  it('should filter by username (case-insensitive)', () => {
    component.filterUsername.set('ALI');
    expect(component.filtered().length).toBe(2);
    expect(component.filtered().every((r) => r.username === 'alice')).toBe(true);
  });

  it('should combine role and username filters', () => {
    component.filterRole.set('OPERATOR');
    component.filterUsername.set('alice');
    expect(component.filtered().length).toBe(2);
  });

  it('should handle load error gracefully', () => {
    getCompetencyMatrix.mockReturnValue(throwError(() => new Error('err')));
    component.ngOnInit();
    expect(component.loading()).toBe(false);
  });
});
