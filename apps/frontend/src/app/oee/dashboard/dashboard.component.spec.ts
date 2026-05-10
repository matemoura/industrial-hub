import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardComponent } from './dashboard.component';
import { WorkerOeeDto } from '../oee.service';

const makeRow = (workerId: number, workerName: string, availability: number | null): WorkerOeeDto => ({
  workerId,
  workerName,
  date: '2026-04-28',
  productiveHours: 4,
  indirectHours: 1,
  shiftDuration: 8,
  availability,
});

describe('DashboardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('formatAvailability returns "—" for null', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    expect(comp.formatAvailability(null)).toBe('—');
  });

  it('formatAvailability formats value as percentage', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    expect(comp.formatAvailability(0.4444)).toBe('44.44%');
    expect(comp.formatAvailability(1)).toBe('100.00%');
    expect(comp.formatAvailability(0)).toBe('0.00%');
  });

  it('filteredRows returns all rows when no worker selected', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    comp.rows.set([makeRow(1, 'Alice', 0.5), makeRow(2, 'Bob', 0.75)]);
    expect(comp.filteredRows().length).toBe(2);
  });

  it('filteredRows filters by selectedWorkerId', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    comp.rows.set([makeRow(1, 'Alice', 0.5), makeRow(2, 'Bob', 0.75)]);
    comp.selectedWorkerId.set(1);
    const result = comp.filteredRows();
    expect(result.length).toBe(1);
    expect(result[0].workerName).toBe('Alice');
  });

  it('workers computed list has one entry per unique workerId', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    comp.rows.set([
      makeRow(1, 'Alice', 0.5),
      { ...makeRow(1, 'Alice', 0.3), date: '2026-04-29' },
      makeRow(2, 'Bob', 0.75),
    ]);
    expect(comp.workers().length).toBe(2);
  });

  it('search does nothing when dates are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    comp.search();
    expect(comp.loading()).toBe(false);
    expect(comp.rows().length).toBe(0);
  });
});
