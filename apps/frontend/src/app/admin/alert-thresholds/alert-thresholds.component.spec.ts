import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { AlertThresholdsComponent } from './alert-thresholds.component';
import { AdminService, AlertThreshold } from '../admin.service';

const MOCK_THRESHOLDS: AlertThreshold[] = [
  {
    id: 'thr-001',
    metric: 'OEE_AVG_BELOW',
    threshold: 65,
    emailEnabled: true,
    active: true,
    updatedAt: '2026-05-01T10:00:00',
  },
  {
    id: 'thr-002',
    metric: 'NC_CRITICAL_ABOVE',
    threshold: 5,
    emailEnabled: false,
    active: true,
    updatedAt: '2026-05-02T10:00:00',
  },
];

function makeAdminService(thresholds: AlertThreshold[] = MOCK_THRESHOLDS) {
  return {
    getThresholds: vi.fn().mockReturnValue(of(thresholds)),
    createThreshold: vi.fn().mockReturnValue(of({ ...MOCK_THRESHOLDS[0], id: 'new-1' })),
    updateThreshold: vi.fn().mockReturnValue(of(MOCK_THRESHOLDS[0])),
    deleteThreshold: vi.fn().mockReturnValue(of(undefined)),
  };
}

describe('AlertThresholdsComponent', () => {
  let fixture: ComponentFixture<AlertThresholdsComponent>;
  let component: AlertThresholdsComponent;
  let adminService: ReturnType<typeof makeAdminService>;

  function setup(thresholds: AlertThreshold[] = MOCK_THRESHOLDS) {
    adminService = makeAdminService(thresholds);
    TestBed.configureTestingModule({
      imports: [AlertThresholdsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AdminService, useValue: adminService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AlertThresholdsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-1 — tabela renderiza thresholds', () => {
    it('should render the thresholds table', () => {
      setup();
      const table = fixture.nativeElement.querySelector('[data-testid="thresholds-table"]');
      expect(table).toBeTruthy();
    });

    it('should render a row for each threshold', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="threshold-row"]');
      expect(rows.length).toBe(MOCK_THRESHOLDS.length);
    });

    it('should display metric label in PT-BR', () => {
      setup();
      const firstRow = fixture.nativeElement.querySelector('[data-testid="threshold-row"]');
      expect(firstRow.textContent).toContain('OEE médio abaixo de (%)');
    });

    it('should show email check icon when emailEnabled = true', () => {
      setup();
      const firstRow = fixture.nativeElement.querySelector('[data-testid="threshold-row"]');
      const checkIcon = firstRow.querySelector('[aria-label="Email habilitado"]');
      expect(checkIcon).toBeTruthy();
    });

    it('should show email x icon when emailEnabled = false', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="threshold-row"]');
      const secondRow = rows[1];
      const xIcon = secondRow.querySelector('[aria-label="Email desabilitado"]');
      expect(xIcon).toBeTruthy();
    });

    it('should show empty state when no thresholds', () => {
      setup([]);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });

    it('should show error state when API fails', () => {
      adminService = {
        getThresholds: vi.fn().mockReturnValue(throwError(() => new Error())),
        createThreshold: vi.fn(),
        updateThreshold: vi.fn(),
        deleteThreshold: vi.fn(),
      };
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [AlertThresholdsComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AdminService, useValue: adminService },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(AlertThresholdsComponent);
      fixture.detectChanges();
      const err = fixture.nativeElement.querySelector('[data-testid="error-state"]');
      expect(err).toBeTruthy();
    });
  });

  describe('AC-2 — botão "Novo Limiar" e dialog de criação', () => {
    it('should show "Novo Limiar" button', () => {
      setup([]);
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-threshold"]');
      expect(btn).toBeTruthy();
    });

    it('should open create dialog on click', () => {
      setup([]);
      fixture.nativeElement.querySelector('[data-testid="btn-new-threshold"]').click();
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="dialog-create"]');
      expect(dialog).toBeTruthy();
    });

    it('should disable "Novo Limiar" button when all metrics already have active thresholds', () => {
      // All 3 metrics are active
      const allActive: AlertThreshold[] = [
        { id: '1', metric: 'OEE_AVG_BELOW', threshold: 65, emailEnabled: true, active: true, updatedAt: '' },
        { id: '2', metric: 'NC_CRITICAL_ABOVE', threshold: 5, emailEnabled: false, active: true, updatedAt: '' },
        { id: '3', metric: 'WO_URGENT_PENDING_HOURS', threshold: 48, emailEnabled: false, active: true, updatedAt: '' },
      ];
      setup(allActive);
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-threshold"]');
      expect(btn.disabled).toBe(true);
    });

    it('should call createThreshold on submit and close dialog', () => {
      setup([]);
      component.openCreateDialog();
      component.formMetric.set('OEE_AVG_BELOW');
      component.formThreshold.set(65);
      component.formEmailEnabled.set(true);
      fixture.detectChanges();
      component.submitCreate();
      expect(adminService.createThreshold).toHaveBeenCalledWith({
        metric: 'OEE_AVG_BELOW',
        threshold: 65,
        emailEnabled: true,
      });
    });

    it('should show validation error if threshold is null on create', () => {
      setup([]);
      component.openCreateDialog();
      component.formThreshold.set(null);
      fixture.detectChanges();
      component.submitCreate();
      expect(component.formError()).toBeTruthy();
      expect(adminService.createThreshold).not.toHaveBeenCalled();
    });
  });

  describe('AC-3 — dialog de edição', () => {
    it('should open edit dialog with pre-filled values', () => {
      setup();
      component.openEditDialog(MOCK_THRESHOLDS[0]);
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="dialog-edit"]');
      expect(dialog).toBeTruthy();
      expect(component.formThreshold()).toBe(65);
      expect(component.formEmailEnabled()).toBe(true);
    });

    it('should call updateThreshold on submit', () => {
      setup();
      component.openEditDialog(MOCK_THRESHOLDS[0]);
      component.formThreshold.set(70);
      component.submitEdit();
      expect(adminService.updateThreshold).toHaveBeenCalledWith('thr-001', {
        threshold: 70,
        emailEnabled: true,
      });
    });
  });

  describe('AC-4 — exclusão', () => {
    it('should open delete confirmation dialog', () => {
      setup();
      component.openDeleteDialog(MOCK_THRESHOLDS[0]);
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="dialog-delete"]');
      expect(dialog).toBeTruthy();
    });

    it('should call deleteThreshold on confirm', () => {
      setup();
      component.openDeleteDialog(MOCK_THRESHOLDS[0]);
      fixture.detectChanges();
      component.confirmDelete();
      expect(adminService.deleteThreshold).toHaveBeenCalledWith('thr-001');
    });

    it('should close dialog on cancel', () => {
      setup();
      component.openDeleteDialog(MOCK_THRESHOLDS[0]);
      fixture.detectChanges();
      component.closeDialog();
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="dialog-delete"]');
      expect(dialog).toBeFalsy();
    });
  });

  describe('AC-5 — edit e delete buttons na tabela', () => {
    it('should render edit button per row', () => {
      setup();
      const editBtns = fixture.nativeElement.querySelectorAll('[data-testid="btn-edit"]');
      expect(editBtns.length).toBe(MOCK_THRESHOLDS.length);
    });

    it('should render delete button per row', () => {
      setup();
      const deleteBtns = fixture.nativeElement.querySelectorAll('[data-testid="btn-delete"]');
      expect(deleteBtns.length).toBe(MOCK_THRESHOLDS.length);
    });
  });
});
