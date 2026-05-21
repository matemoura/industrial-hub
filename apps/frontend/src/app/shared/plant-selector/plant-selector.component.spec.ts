import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlantSelectorComponent, PlantOption } from './plant-selector.component';
import { PlantContextService } from './plant-context.service';

const MOCK_PLANTS: PlantOption[] = [
  { id: 'plant-001', name: 'Matriz', code: 'HQ' },
  { id: 'plant-002', name: 'Filial Rio', code: 'FIL' },
  { id: 'plant-003', name: 'Filial BH', code: 'BHZ' },
];

describe('PlantSelectorComponent', () => {
  let fixture: ComponentFixture<PlantSelectorComponent>;
  let component: PlantSelectorComponent;
  let plantContext: PlantContextService;

  function setup(plants: PlantOption[] = MOCK_PLANTS) {
    TestBed.configureTestingModule({
      imports: [PlantSelectorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PlantSelectorComponent);
    component = fixture.componentInstance;
    plantContext = TestBed.inject(PlantContextService);

    // Reset localStorage
    localStorage.removeItem('msb_plant_id');
    plantContext.setPlant(null);

    fixture.componentRef.setInput('plants', plants);
    fixture.detectChanges();
  }

  afterEach(() => {
    localStorage.removeItem('msb_plant_id');
  });

  describe('AC-1 — visibilidade do seletor', () => {
    it('should show selector when user has more than 1 plant', () => {
      setup(MOCK_PLANTS);
      const selector = fixture.nativeElement.querySelector('[data-testid="plant-selector"]');
      expect(selector).toBeTruthy();
    });

    it('should NOT show selector when user has exactly 1 plant', () => {
      setup([MOCK_PLANTS[0]]);
      const selector = fixture.nativeElement.querySelector('[data-testid="plant-selector"]');
      expect(selector).toBeNull();
    });

    it('should NOT show selector when user has no plants', () => {
      setup([]);
      const selector = fixture.nativeElement.querySelector('[data-testid="plant-selector"]');
      expect(selector).toBeNull();
    });
  });

  describe('AC-2 — chips de plantas', () => {
    it('should render "Todas" chip', () => {
      setup(MOCK_PLANTS);
      const allChip = fixture.nativeElement.querySelector('[data-testid="chip-all"]');
      expect(allChip).toBeTruthy();
      expect(allChip.textContent.trim()).toBe('Todas');
    });

    it('should render a chip for each plant', () => {
      setup(MOCK_PLANTS);
      const allChip = fixture.nativeElement.querySelector('[data-testid="chip-all"]');
      const plantChips = fixture.nativeElement.querySelectorAll('.plant-chip:not([data-testid="chip-all"])');
      expect(allChip).toBeTruthy();
      expect(plantChips.length).toBe(MOCK_PLANTS.length);
    });
  });

  describe('AC-3 — seleção de planta', () => {
    it('should update selectedPlantId when chip is clicked', () => {
      setup(MOCK_PLANTS);
      component.selectPlant('plant-001');
      expect(plantContext.selectedPlantId()).toBe('plant-001');
    });

    it('should emit plantSelected event when chip is clicked', () => {
      setup(MOCK_PLANTS);
      const emitted: (string | null)[] = [];
      fixture.componentInstance.plantSelected.subscribe((id: string | null) => emitted.push(id));
      component.selectPlant('plant-002');
      expect(emitted).toContain('plant-002');
    });

    it('should reset to null when "Todas" is clicked', () => {
      setup(MOCK_PLANTS);
      component.selectPlant('plant-001');
      component.selectPlant(null);
      expect(plantContext.selectedPlantId()).toBeNull();
    });
  });

  describe('AC-4 — persistência no localStorage', () => {
    it('should persist selection in localStorage', () => {
      setup(MOCK_PLANTS);
      component.selectPlant('plant-001');
      expect(localStorage.getItem('msb_plant_id')).toBe('plant-001');
    });

    it('should remove from localStorage when null selected', () => {
      setup(MOCK_PLANTS);
      localStorage.setItem('msb_plant_id', 'plant-001');
      component.selectPlant(null);
      expect(localStorage.getItem('msb_plant_id')).toBeNull();
    });
  });
});
