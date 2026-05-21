import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { PlantContextService } from './plant-context.service';
import { plantHeaderInterceptor } from './plant-header.interceptor';

describe('plantHeaderInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let plantContext: PlantContextService;

  beforeEach(() => {
    localStorage.removeItem('msb_plant_id');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([plantHeaderInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    plantContext = TestBed.inject(PlantContextService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('msb_plant_id');
  });

  describe('AC-1 — adiciona header X-Plant-Id quando planta selecionada', () => {
    it('should add X-Plant-Id header for /api/ requests when plant selected', () => {
      plantContext.setPlant('plant-001');

      httpClient.get('/api/v1/maintenance/equipment').subscribe();

      const req = httpMock.expectOne('/api/v1/maintenance/equipment');
      expect(req.request.headers.get('X-Plant-Id')).toBe('plant-001');
      req.flush([]);
    });

    it('should NOT add X-Plant-Id header when no plant selected', () => {
      plantContext.setPlant(null);

      httpClient.get('/api/v1/maintenance/equipment').subscribe();

      const req = httpMock.expectOne('/api/v1/maintenance/equipment');
      expect(req.request.headers.get('X-Plant-Id')).toBeNull();
      req.flush([]);
    });
  });

  describe('AC-2 — não interfere em requests fora de /api/', () => {
    it('should NOT add X-Plant-Id header for non-api requests', () => {
      plantContext.setPlant('plant-001');

      httpClient.get('/assets/config.json').subscribe();

      const req = httpMock.expectOne('/assets/config.json');
      expect(req.request.headers.get('X-Plant-Id')).toBeNull();
      req.flush({});
    });
  });

  describe('SEC-080 — excluir /auth/ do interceptor', () => {
    it('should NOT add X-Plant-Id header for /api/v1/auth/login even when plant is selected', () => {
      plantContext.setPlant('plant-001');

      httpClient.post('/api/v1/auth/login', { username: 'admin', password: 'admin' }).subscribe();

      const req = httpMock.expectOne('/api/v1/auth/login');
      expect(req.request.headers.get('X-Plant-Id')).toBeNull();
      req.flush({ token: 'jwt' });
    });

    it('should add X-Plant-Id header for normal API requests when plant is selected', () => {
      plantContext.setPlant('plant-001');

      httpClient.get('/api/v1/maintenance/equipment').subscribe();

      const req = httpMock.expectOne('/api/v1/maintenance/equipment');
      expect(req.request.headers.get('X-Plant-Id')).toBe('plant-001');
      req.flush([]);
    });
  });
});
