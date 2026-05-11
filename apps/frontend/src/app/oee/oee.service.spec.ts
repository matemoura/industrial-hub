import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OeeService } from './oee.service';

describe('OeeService', () => {
  let service: OeeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OeeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getDashboard sends startDate and endDate params', () => {
    service.getDashboard('2026-04-01', '2026-04-30').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/dashboard'));
    expect(req.request.params.get('startDate')).toBe('2026-04-01');
    expect(req.request.params.get('endDate')).toBe('2026-04-30');
    expect(req.request.params.has('workerId')).toBe(false);
    req.flush([]);
  });

  it('getDashboard includes workerId when provided', () => {
    service.getDashboard('2026-04-01', '2026-04-30', 1001).subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/dashboard'));
    expect(req.request.params.get('workerId')).toBe('1001');
    req.flush([]);
  });

  it('getIndirectActivities sends correct params', () => {
    service.getIndirectActivities('2026-04-01', '2026-04-30').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/indirect-activities'));
    expect(req.request.params.get('startDate')).toBe('2026-04-01');
    expect(req.request.params.get('endDate')).toBe('2026-04-30');
    expect(req.request.params.has('workerId')).toBe(false);
    req.flush([]);
  });

  it('getIndirectActivities includes workerId when provided', () => {
    service.getIndirectActivities('2026-04-01', '2026-04-30', 2001).subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/indirect-activities'));
    expect(req.request.params.get('workerId')).toBe('2001');
    req.flush([]);
  });

  it('getSummary sends correct params with default groupBy DAY', () => {
    service.getSummary('2026-04-01', '2026-04-30').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/summary'));
    expect(req.request.params.get('startDate')).toBe('2026-04-01');
    expect(req.request.params.get('groupBy')).toBe('DAY');
    req.flush([]);
  });

  it('getSummary sends WEEK groupBy when specified', () => {
    service.getSummary('2026-04-01', '2026-04-30', 'WEEK').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/summary'));
    expect(req.request.params.get('groupBy')).toBe('WEEK');
    req.flush([]);
  });

  it('getByProcess sends startDate and endDate params', () => {
    service.getByProcess('2026-04-01', '2026-04-30').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/by-process'));
    expect(req.request.params.get('startDate')).toBe('2026-04-01');
    expect(req.request.params.get('endDate')).toBe('2026-04-30');
    expect(req.request.params.has('workerId')).toBe(false);
    req.flush([]);
  });

  it('getByProcess includes workerId when provided', () => {
    service.getByProcess('2026-04-01', '2026-04-30', 1001).subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/by-process'));
    expect(req.request.params.get('workerId')).toBe('1001');
    req.flush([]);
  });

  it('getWorkers calls /api/v1/workers', () => {
    service.getWorkers().subscribe();
    const req = httpMock.expectOne('/api/v1/workers');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('exportDashboard sends correct params and returns blob', () => {
    service.exportDashboard('2026-04-01', '2026-04-30').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/dashboard/export'));
    expect(req.request.params.get('startDate')).toBe('2026-04-01');
    expect(req.request.params.get('endDate')).toBe('2026-04-30');
    expect(req.request.params.has('workerId')).toBe(false);
    req.flush(new Blob());
  });

  it('exportDashboard includes workerId when provided', () => {
    service.exportDashboard('2026-04-01', '2026-04-30', 1001).subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/dashboard/export'));
    expect(req.request.params.get('workerId')).toBe('1001');
    req.flush(new Blob());
  });

  it('exportSummary sends correct params with groupBy', () => {
    service.exportSummary('2026-04-01', '2026-04-30', 'WEEK').subscribe();
    const req = httpMock.expectOne((r) => r.url.includes('/api/v1/oee/summary/export'));
    expect(req.request.params.get('groupBy')).toBe('WEEK');
    req.flush(new Blob());
  });
});
