import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { PlantListComponent } from './plant-list.component';
import { PlantResponse, PlantService } from './plant.service';

const MOCK_PLANTS: PlantResponse[] = [
  {
    id: 'plant-001',
    code: 'HQ',
    name: 'Matriz',
    address: 'Av. Industrial, 1000 — São Paulo, SP',
    timezone: 'America/Sao_Paulo',
    isDefault: true,
    active: true,
  },
  {
    id: 'plant-002',
    code: 'FIL',
    name: 'Filial Rio',
    address: null,
    timezone: 'America/Sao_Paulo',
    isDefault: false,
    active: true,
  },
];

function makePlantService(plants: PlantResponse[] = MOCK_PLANTS) {
  return {
    listPlants: vi.fn().mockReturnValue(of(plants)),
    createPlant: vi.fn().mockReturnValue(of({ ...MOCK_PLANTS[0], id: 'new-1' })),
    updatePlant: vi.fn().mockReturnValue(of(MOCK_PLANTS[0])),
    deactivatePlant: vi.fn().mockReturnValue(of(undefined)),
    getUserPlants: vi.fn().mockReturnValue(of([])),
    updateUserPlants: vi.fn().mockReturnValue(of(undefined)),
  };
}

describe('PlantListComponent', () => {
  let fixture: ComponentFixture<PlantListComponent>;
  let component: PlantListComponent;
  let plantService: ReturnType<typeof makePlantService>;

  function setup(plants: PlantResponse[] = MOCK_PLANTS) {
    plantService = makePlantService(plants);
    TestBed.configureTestingModule({
      imports: [PlantListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PlantService, useValue: plantService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlantListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-1 — listagem de plantas', () => {
    it('should render plants-table when plants are loaded', () => {
      setup();
      const table = fixture.nativeElement.querySelector('[data-testid="plants-table"]');
      expect(table).toBeTruthy();
    });

    it('should render a row for each plant', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="plant-row"]');
      expect(rows.length).toBe(MOCK_PLANTS.length);
    });

    it('should display plant code', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="plant-row"]');
      expect(rows[0].textContent).toContain('HQ');
    });

    it('should display plant name', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="plant-row"]');
      expect(rows[0].textContent).toContain('Matriz');
    });

    it('should show empty-state when no plants', () => {
      setup([]);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });

    it('should show error-state when API fails', () => {
      plantService = makePlantService();
      plantService.listPlants = vi.fn().mockReturnValue(throwError(() => new Error('API error')));
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [PlantListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: PlantService, useValue: plantService },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(PlantListComponent);
      fixture.detectChanges();
      const err = fixture.nativeElement.querySelector('[data-testid="error-state"]');
      expect(err).toBeTruthy();
    });
  });

  describe('AC-2 — criação de planta', () => {
    it('should disable submit button when name is empty', () => {
      setup([]);
      component.openCreateDialog();
      component.formCode.set('TST');
      component.formName.set('');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(true);
    });

    it('should disable submit button when code is empty', () => {
      setup([]);
      component.openCreateDialog();
      component.formCode.set('');
      component.formName.set('Test Plant');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(true);
    });

    it('should enable submit button when form is valid', () => {
      setup([]);
      component.openCreateDialog();
      component.formCode.set('TST');
      component.formName.set('Test Plant');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(false);
    });

    it('should call createPlant when valid form submitted', () => {
      setup([]);
      component.openCreateDialog();
      component.formCode.set('TST');
      component.formName.set('Test Plant');
      component.formAddress.set('');
      component.formTimezone.set('America/Sao_Paulo');
      component.submitCreate();
      expect(plantService.createPlant).toHaveBeenCalledWith({
        code: 'TST',
        name: 'Test Plant',
        address: null,
        timezone: 'America/Sao_Paulo',
      });
    });
  });

  describe('AC-3 — desativação de planta', () => {
    it('should disable deactivate button for default plant', () => {
      setup();
      fixture.detectChanges();
      const buttons = fixture.nativeElement.querySelectorAll('[data-testid="btn-deactivate"]');
      // First plant is isDefault=true
      expect(buttons[0].disabled).toBe(true);
    });

    it('should NOT disable deactivate button for non-default plant', () => {
      setup();
      fixture.detectChanges();
      const buttons = fixture.nativeElement.querySelectorAll('[data-testid="btn-deactivate"]');
      // Second plant is isDefault=false
      expect(buttons[1].disabled).toBe(false);
    });

    it('should call deactivatePlant when user confirms', () => {
      setup();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.deactivate(MOCK_PLANTS[1]);
      expect(plantService.deactivatePlant).toHaveBeenCalledWith('plant-002');
    });

    it('should NOT call deactivatePlant when user cancels', () => {
      setup();
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      component.deactivate(MOCK_PLANTS[1]);
      expect(plantService.deactivatePlant).not.toHaveBeenCalled();
    });

    it('should NOT call deactivatePlant for default plant even if method called directly', () => {
      setup();
      component.deactivate(MOCK_PLANTS[0]); // isDefault=true
      expect(plantService.deactivatePlant).not.toHaveBeenCalled();
    });
  });

  describe('AC-4 — guard ADMIN (via tabela e botão)', () => {
    it('should render btn-new-plant', () => {
      setup();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-plant"]');
      expect(btn).toBeTruthy();
    });
  });

  describe('AC-5 — loading state', () => {
    it('should show loading skeleton initially', () => {
      plantService = makePlantService();
      // Keep observable open to simulate loading
      const subject = new Subject<PlantResponse[]>();
      plantService.listPlants = vi.fn().mockReturnValue(subject.asObservable());

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [PlantListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: PlantService, useValue: plantService },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(PlantListComponent);
      fixture.detectChanges();
      const skeleton = fixture.nativeElement.querySelector('[data-testid="loading-state"]');
      expect(skeleton).toBeTruthy();
    });
  });

  describe('AC-6 — success message after create', () => {
    it('should show success message after successful creation', () => {
      setup([]);
      component.openCreateDialog();
      component.formCode.set('TST');
      component.formName.set('Test Plant');
      component.submitCreate();
      fixture.detectChanges();
      const msg = fixture.nativeElement.querySelector('[data-testid="success-msg"]');
      expect(msg).toBeTruthy();
    });
  });

  describe('AC-7 — code uppercased automatically', () => {
    it('should convert code to uppercase', () => {
      setup();
      component.updateFormCode('tst');
      expect(component.formCode()).toBe('TST');
    });
  });

  describe('AC-8 — edit dialog', () => {
    it('should open edit dialog with plant data', () => {
      setup();
      component.openEditDialog(MOCK_PLANTS[0]);
      expect(component.dialogMode()).toBe('edit');
      expect(component.editingPlant()).toBe(MOCK_PLANTS[0]);
      expect(component.formName()).toBe('Matriz');
    });

    it('should call updatePlant when valid edit submitted', () => {
      setup();
      component.openEditDialog(MOCK_PLANTS[0]);
      component.formName.set('Matriz Atualizada');
      component.submitEdit();
      expect(plantService.updatePlant).toHaveBeenCalledWith('plant-001', {
        name: 'Matriz Atualizada',
        address: 'Av. Industrial, 1000 — São Paulo, SP',
        timezone: 'America/Sao_Paulo',
      });
    });
  });
});
