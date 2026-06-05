import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrainingCourseListComponent } from './training-course-list.component';
import { TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockPage = {
  content: [
    { id: '1', code: 'GMP-001', title: 'GMP Básico', category: 'GMP', durationHours: 8, active: true, requiredForRoles: ['OPERATOR'], createdAt: '2024-01-01T00:00:00' },
    { id: '2', code: 'QA-001', title: 'Qualidade', category: 'QUALITY', durationHours: 16, active: false, requiredForRoles: ['SUPERVISOR'], createdAt: '2024-01-02T00:00:00' },
  ],
  totalPages: 1,
  number: 0,
  totalElements: 2,
};

describe('TrainingCourseListComponent', () => {
  let fixture: ComponentFixture<TrainingCourseListComponent>;
  let component: TrainingCourseListComponent;
  let getCourses: ReturnType<typeof vi.fn>;
  let authRole: ReturnType<typeof signal<string>>;

  beforeEach(async () => {
    getCourses = vi.fn().mockReturnValue(of(mockPage));
    authRole = signal('OPERATOR');

    const service = { getCourses };

    await TestBed.configureTestingModule({
      imports: [TrainingCourseListComponent],
      providers: [
        provideRouter([]),
        { provide: TrainingService, useValue: service },
        { provide: AuthService, useValue: { role: authRole } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingCourseListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load courses on init', () => {
    expect(getCourses).toHaveBeenCalledWith(0, 100);
    expect(component.courses().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('should filter by category', () => {
    component.filterCategory.set('GMP');
    expect(component.filtered().length).toBe(1);
    expect(component.filtered()[0].code).toBe('GMP-001');
  });

  it('should filter by active status', () => {
    component.filterActive.set('false');
    expect(component.filtered().length).toBe(1);
    expect(component.filtered()[0].code).toBe('QA-001');
  });

  it('should show "Novo Curso" button for admin', () => {
    authRole.set('ADMIN');
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button');
    expect(btn).toBeTruthy();
  });

  it('should handle load error gracefully', () => {
    getCourses.mockReturnValue(throwError(() => new Error('err')));
    component.load(0);
    expect(component.loading()).toBe(false);
  });
});
