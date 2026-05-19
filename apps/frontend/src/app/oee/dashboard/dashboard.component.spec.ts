import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { WorkerDto, WorkerOeeDto } from '../oee.service';

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
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
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

  it('ngOnInit loads workers from API into allWorkers', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const httpTesting = TestBed.inject(HttpTestingController);

    fixture.detectChanges(); // triggers ngOnInit

    const workers: WorkerDto[] = [
      { workerId: 1001, workerName: 'Alice' },
      { workerId: 1002, workerName: 'Bob' },
    ];
    httpTesting.expectOne('/api/v1/workers').flush(workers);

    expect(fixture.componentInstance.allWorkers().length).toBe(2);
    expect(fixture.componentInstance.allWorkers()[0].workerName).toBe('Alice');

    httpTesting.verify();
  });

  it('searchWorkerId starts as null (All workers default)', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    expect(comp.searchWorkerId()).toBeNull();
  });

  it('exportCsv does nothing when dates are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    // should not throw — just guards and returns
    expect(() => comp.exportCsv()).not.toThrow();
  });

  it('excludePlannedDowntime starts as false', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    expect(comp.excludePlannedDowntime()).toBe(false);
  });

  it('should render toggle-exclude-downtime checkbox', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    const httpTesting = TestBed.inject(HttpTestingController);
    httpTesting.expectOne('/api/v1/workers').flush([]);
    const toggle = fixture.nativeElement.querySelector('[data-testid="toggle-exclude-downtime"]');
    expect(toggle).toBeTruthy();
    httpTesting.verify();
  });

  it('toggle-exclude-downtime signal starts false and can be set to true', () => {
    const { componentInstance: comp } = TestBed.createComponent(DashboardComponent);
    expect(comp.excludePlannedDowntime()).toBe(false);
    comp.excludePlannedDowntime.set(true);
    expect(comp.excludePlannedDowntime()).toBe(true);
  });
});
