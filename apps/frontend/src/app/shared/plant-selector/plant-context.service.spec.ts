import { TestBed } from '@angular/core/testing';
import { PlantContextService } from './plant-context.service';

describe('PlantContextService', () => {
  let service: PlantContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(PlantContextService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('AC-1 — setPlant e selectedPlantId', () => {
    it('should initialize selectedPlantId as null when localStorage is empty', () => {
      expect(service.selectedPlantId()).toBeNull();
    });

    it('should update signal when setPlant is called', () => {
      service.setPlant('plant-001');
      expect(service.selectedPlantId()).toBe('plant-001');
    });

    it('should reset signal to null when setPlant(null) is called', () => {
      service.setPlant('plant-001');
      service.setPlant(null);
      expect(service.selectedPlantId()).toBeNull();
    });
  });

  describe('AC-2 — getHeader', () => {
    it('should return X-Plant-Id header when plant is selected', () => {
      service.setPlant('plant-001');
      const headers = service.getHeader();
      expect(headers).toEqual({ 'X-Plant-Id': 'plant-001' });
    });

    it('should return empty object when no plant selected', () => {
      service.setPlant(null);
      const headers = service.getHeader();
      expect(headers).toEqual({});
    });
  });

  describe('AC-3 — persistência no localStorage', () => {
    it('should read stored id from localStorage on initialization', () => {
      localStorage.setItem('msb_plant_id', 'stored-plant-id');
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({});
      const freshService = TestBed.inject(PlantContextService);
      expect(freshService.selectedPlantId()).toBe('stored-plant-id');
    });

    it('should persist plant id to localStorage when setPlant is called', () => {
      service.setPlant('plant-xyz');
      expect(localStorage.getItem('msb_plant_id')).toBe('plant-xyz');
    });

    it('should remove plant id from localStorage when setPlant(null) is called', () => {
      service.setPlant('plant-xyz');
      service.setPlant(null);
      expect(localStorage.getItem('msb_plant_id')).toBeNull();
    });
  });

  describe('AC-4 — UUID stale inválido', () => {
    it('should allow external code to invalidate a stale stored id via setPlant(null)', () => {
      // Simula UUID stale: salvo em localStorage mas não está na lista de plantas ativas
      service.setPlant('stale-plant-id-not-in-server');
      expect(service.selectedPlantId()).toBe('stale-plant-id-not-in-server');

      // Simula o nav detectando que o id não está na lista e invalidando
      const activePlants = [{ id: 'plant-active-001' }, { id: 'plant-active-002' }];
      const storedId = service.selectedPlantId();
      if (storedId && !activePlants.find((p) => p.id === storedId)) {
        service.setPlant(null);
      }

      expect(service.selectedPlantId()).toBeNull();
      expect(localStorage.getItem('msb_plant_id')).toBeNull();
    });

    it('should NOT invalidate a valid stored id that matches an active plant', () => {
      service.setPlant('plant-active-001');
      const activePlants = [{ id: 'plant-active-001' }, { id: 'plant-active-002' }];
      const storedId = service.selectedPlantId();
      if (storedId && !activePlants.find((p) => p.id === storedId)) {
        service.setPlant(null);
      }
      expect(service.selectedPlantId()).toBe('plant-active-001');
    });
  });
});
