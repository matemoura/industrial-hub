import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SummaryComponent } from './summary.component';
import { PeriodSummaryDto } from '../oee.service';

const makeRow = (period: string, avgAvailability: number | null, workerCount: number): PeriodSummaryDto => ({
  period, avgAvailability, workerCount,
});

describe('SummaryComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SummaryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SummaryComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('formatAvailability returns "—" for null', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    expect(comp.formatAvailability(null)).toBe('—');
  });

  it('formatAvailability formats as percentage', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    expect(comp.formatAvailability(0.625)).toBe('62.50%');
    expect(comp.formatAvailability(1)).toBe('100.00%');
  });

  it('default groupBy is DAY', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    expect(comp.groupBy()).toBe('DAY');
  });

  it('groupByOptions contains DAY, WEEK, MONTH', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    expect(comp.groupByOptions).toEqual(['DAY', 'WEEK', 'MONTH']);
  });

  it('search does nothing when dates are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    comp.search();
    expect(comp.loading()).toBe(false);
  });

  it('rows signal updates correctly', () => {
    const { componentInstance: comp } = TestBed.createComponent(SummaryComponent);
    comp.rows.set([makeRow('2026-04', 0.75, 10), makeRow('2026-05', null, 8)]);
    expect(comp.rows().length).toBe(2);
    expect(comp.rows()[1].avgAvailability).toBeNull();
  });
});
