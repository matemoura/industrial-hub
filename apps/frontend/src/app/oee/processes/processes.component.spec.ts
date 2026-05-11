import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProcessesComponent } from './processes.component';
import { ProcessEfficiencyDto, WorkerDto } from '../oee.service';

const makeRow = (description: string, totalHours: number, workerCount: number, occurrences: number): ProcessEfficiencyDto => ({
  description,
  totalHours,
  workerCount,
  occurrences,
});

describe('ProcessesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProcessesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ProcessesComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('rows starts empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(ProcessesComponent);
    expect(comp.rows().length).toBe(0);
  });

  it('searchWorkerId starts as null (All workers default)', () => {
    const { componentInstance: comp } = TestBed.createComponent(ProcessesComponent);
    expect(comp.searchWorkerId()).toBeNull();
  });

  it('search does nothing when dates are empty', () => {
    const { componentInstance: comp } = TestBed.createComponent(ProcessesComponent);
    comp.search();
    expect(comp.loading()).toBe(false);
    expect(comp.rows().length).toBe(0);
  });

  it('formatHours formats with 2 decimals and h suffix', () => {
    const { componentInstance: comp } = TestBed.createComponent(ProcessesComponent);
    expect(comp.formatHours(12.5)).toBe('12.50h');
    expect(comp.formatHours(0)).toBe('0.00h');
    expect(comp.formatHours(1.333)).toBe('1.33h');
  });

  it('ngOnInit loads workers from API into allWorkers', () => {
    const fixture = TestBed.createComponent(ProcessesComponent);
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

  it('rows signal updates correctly after set', () => {
    const { componentInstance: comp } = TestBed.createComponent(ProcessesComponent);
    comp.rows.set([
      makeRow('Montagem Fibra Laser', 12.5, 3, 8),
      makeRow('Solda TIG', 4.0, 2, 4),
    ]);
    expect(comp.rows().length).toBe(2);
    expect(comp.rows()[0].description).toBe('Montagem Fibra Laser');
    expect(comp.rows()[0].workerCount).toBe(3);
  });
});
