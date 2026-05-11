import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { IndirectActivitiesComponent } from './indirect-activities.component';
import { IndirectActivityDto } from '../oee.service';

const makeRow = (description: string, occurrences: number, totalHours: number, percentOfTotal: number): IndirectActivityDto => ({
  description, occurrences, totalHours, percentOfTotal,
});

describe('IndirectActivitiesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IndirectActivitiesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(IndirectActivitiesComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('formatPercent returns two-decimal percentage string', () => {
    const { componentInstance: comp } = TestBed.createComponent(IndirectActivitiesComponent);
    expect(comp.formatPercent(0.2345)).toBe('23.45%');
    expect(comp.formatPercent(0)).toBe('0.00%');
    expect(comp.formatPercent(1)).toBe('100.00%');
  });

  it('rows starts empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(IndirectActivitiesComponent);
    expect(comp.rows().length).toBe(0);
  });

  it('search does nothing when dates are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(IndirectActivitiesComponent);
    comp.search();
    expect(comp.loading()).toBe(false);
  });

  it('rows signal updates correctly', () => {
    const { componentInstance: comp } = TestBed.createComponent(IndirectActivitiesComponent);
    comp.rows.set([makeRow('Setup', 3, 12.5, 0.4167)]);
    expect(comp.rows().length).toBe(1);
    expect(comp.rows()[0].description).toBe('Setup');
  });
});
