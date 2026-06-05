import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrainingCourseFormComponent } from './training-course-form.component';
import { TrainingService } from '../training.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockCourse = {
  id: 'c1', code: 'GMP-001', title: 'GMP Básico', description: '', category: 'GMP',
  durationHours: 8, validityMonths: 12, requiredForRoles: ['OPERATOR'], active: true, createdAt: '2024-01-01T00:00:00',
};

describe('TrainingCourseFormComponent — new mode', () => {
  let fixture: ComponentFixture<TrainingCourseFormComponent>;
  let component: TrainingCourseFormComponent;

  beforeEach(async () => {
    const service = {
      createCourse: vi.fn(),
      getCourse: vi.fn(),
      updateCourse: vi.fn(),
      deactivateCourse: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [TrainingCourseFormComponent],
      providers: [
        provideRouter([]),
        { provide: TrainingService, useValue: service },
        { provide: AuthService, useValue: { role: signal('ADMIN') } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingCourseFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create in new mode', () => {
    expect(component).toBeTruthy();
    expect(component.isEdit()).toBe(false);
  });

  it('should start with no courseId', () => {
    expect(component.courseId()).toBeNull();
  });
});

describe('TrainingCourseFormComponent — edit mode', () => {
  let fixture: ComponentFixture<TrainingCourseFormComponent>;
  let component: TrainingCourseFormComponent;
  let getCourse: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getCourse = vi.fn().mockReturnValue(of(mockCourse));
    const service = {
      createCourse: vi.fn(),
      getCourse,
      updateCourse: vi.fn(),
      deactivateCourse: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [TrainingCourseFormComponent],
      providers: [
        provideRouter([]),
        { provide: TrainingService, useValue: service },
        { provide: AuthService, useValue: { role: signal('ADMIN') } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'c1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingCourseFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create in edit mode', () => {
    expect(component.isEdit()).toBe(true);
    expect(component.courseId()).toBe('c1');
  });

  it('should load course data on init', () => {
    expect(getCourse).toHaveBeenCalledWith('c1');
    expect(component.code()).toBe('GMP-001');
    expect(component.title()).toBe('GMP Básico');
    expect(component.durationHours()).toBe(8);
  });

  it('should handle load error gracefully', () => {
    getCourse.mockReturnValue(throwError(() => new Error('err')));
    fixture.detectChanges();
    expect(component.loading()).toBe(false);
  });
});
