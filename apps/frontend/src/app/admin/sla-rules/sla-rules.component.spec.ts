import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { SlaRulesComponent } from './sla-rules.component';
import { SlaRuleResponse, SlaService } from './sla.service';

const MOCK_RULES: SlaRuleResponse[] = [
  {
    id: 'sla-001',
    entityType: 'NC',
    classifierField: 'SEVERITY',
    classifierValue: 'CRITICAL',
    slaHours: 48,
    escalateByEmail: true,
    active: true,
  },
  {
    id: 'sla-002',
    entityType: 'WORK_ORDER',
    classifierField: 'PRIORITY',
    classifierValue: 'URGENT',
    slaHours: 4,
    escalateByEmail: false,
    active: true,
  },
];

function makeSlaService(rules: SlaRuleResponse[] = MOCK_RULES) {
  return {
    listSlaRules: vi.fn().mockReturnValue(of(rules)),
    createSlaRule: vi.fn().mockReturnValue(of({ ...MOCK_RULES[0], id: 'new-1' })),
    updateSlaRule: vi.fn().mockReturnValue(of(MOCK_RULES[0])),
    deleteSlaRule: vi.fn().mockReturnValue(of(undefined)),
    runEscalationNow: vi.fn().mockReturnValue(of({ breachedNcs: 3, breachedWorkOrders: 1 })),
    getSlaSummary: vi.fn().mockReturnValue(of({ totalBreachedNcs: 2, totalBreachedWorkOrders: 1, totalOpenNcs: 5, totalOpenWorkOrders: 10 })),
  };
}

describe('SlaRulesComponent', () => {
  let fixture: ComponentFixture<SlaRulesComponent>;
  let component: SlaRulesComponent;
  let slaService: ReturnType<typeof makeSlaService>;

  function setup(rules: SlaRuleResponse[] = MOCK_RULES) {
    slaService = makeSlaService(rules);
    TestBed.configureTestingModule({
      imports: [SlaRulesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SlaService, useValue: slaService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SlaRulesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-1 — tabela exibe regras mockadas', () => {
    it('should render sla-rules-table', () => {
      setup();
      const table = fixture.nativeElement.querySelector('[data-testid="sla-rules-table"]');
      expect(table).toBeTruthy();
    });

    it('should render a row for each rule', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="sla-rule-row"]');
      expect(rows.length).toBe(MOCK_RULES.length);
    });

    it('should display entityType in first row', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="sla-rule-row"]');
      expect(rows[0].textContent).toContain('Não-Conformidade');
    });

    it('should display slaHours in first row', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="sla-rule-row"]');
      expect(rows[0].textContent).toContain('48h');
    });
  });

  describe('AC-2 — botão salvar desabilitado com slaHours < 1', () => {
    it('should disable submit button when formSlaHours is null', () => {
      setup([]);
      component.openCreateDialog();
      component.formSlaHours.set(null);
      component.formClassifierValue.set('CRITICAL');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(true);
    });

    it('should disable submit button when formSlaHours is 0', () => {
      setup([]);
      component.openCreateDialog();
      component.formSlaHours.set(0);
      component.formClassifierValue.set('CRITICAL');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(true);
    });

    it('should enable submit button when form is valid', () => {
      setup([]);
      component.openCreateDialog();
      component.formSlaHours.set(48);
      component.formClassifierValue.set('CRITICAL');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-create"]');
      expect(btn.disabled).toBe(false);
    });
  });

  describe('AC-3 — PUT disparado ao salvar inline edit', () => {
    it('should call updateSlaRule when saving inline edit', () => {
      setup();
      component.startEdit(MOCK_RULES[0]);
      component.editState.update((s) => s ? { ...s, slaHours: 72, escalateByEmail: false } : s);
      component.saveEdit();
      expect(slaService.updateSlaRule).toHaveBeenCalledWith('sla-001', {
        slaHours: 72,
        escalateByEmail: false,
      });
    });

    it('should not call updateSlaRule if slaHours is invalid', () => {
      setup();
      component.startEdit(MOCK_RULES[0]);
      component.editState.update((s) => s ? { ...s, slaHours: null } : s);
      component.saveEdit();
      expect(slaService.updateSlaRule).not.toHaveBeenCalled();
    });
  });

  describe('AC-4 — confirmação antes de DELETE', () => {
    it('should call deleteSlaRule when user confirms', () => {
      setup();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.deactivate(MOCK_RULES[0]);
      expect(slaService.deleteSlaRule).toHaveBeenCalledWith('sla-001');
    });

    it('should NOT call deleteSlaRule when user cancels', () => {
      setup();
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      component.deactivate(MOCK_RULES[0]);
      expect(slaService.deleteSlaRule).not.toHaveBeenCalled();
    });
  });

  describe('AC-5 — estados de erro e vazio', () => {
    it('should show empty state when no rules', () => {
      setup([]);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });

    it('should show error state when API fails', () => {
      slaService = {
        ...makeSlaService(),
        listSlaRules: vi.fn().mockReturnValue(throwError(() => new Error())),
      };
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [SlaRulesComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: SlaService, useValue: slaService },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(SlaRulesComponent);
      fixture.detectChanges();
      const err = fixture.nativeElement.querySelector('[data-testid="error-state"]');
      expect(err).toBeTruthy();
    });
  });

  describe('AC-6 — runEscalationNow', () => {
    it('should call runEscalationNow and show success message', () => {
      setup();
      component.runEscalation();
      expect(slaService.runEscalationNow).toHaveBeenCalled();
      expect(component.successMsg()).toContain('3 NCs');
    });
  });
});
