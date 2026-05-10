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
});
