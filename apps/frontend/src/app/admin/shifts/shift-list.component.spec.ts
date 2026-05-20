import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ShiftListComponent } from './shift-list.component';
import { AdminService, Shift } from '../admin.service';

const MOCK_SHIFTS: Shift[] = [
  { id: '1', name: 'Manhã',  startTime: '06:00', endTime: '14:00', overnight: false, active: true },
  { id: '2', name: 'Tarde',  startTime: '14:00', endTime: '22:00', overnight: false, active: true },
  { id: '3', name: 'Noite',  startTime: '22:00', endTime: '06:00', overnight: true,  active: true },
];

function makeService(overrides: Partial<{ getShifts: unknown; createShift: unknown; deactivateShift: unknown }> = {}) {
  return {
    getShifts:       vi.fn().mockReturnValue(of(MOCK_SHIFTS)),
    createShift:     vi.fn().mockReturnValue(of(MOCK_SHIFTS[0])),
    deactivateShift: vi.fn().mockReturnValue(of(void 0)),
    ...overrides,
  };
}

describe('ShiftListComponent', () => {

  // ─── (a) Tabela exibe 3 turnos mockados ─────────────────────────────────────
  describe('(a) tabela com 3 turnos mockados', () => {
    let fixture: ComponentFixture<ShiftListComponent>;

    beforeEach(async () => {
      const service = makeService();
      await TestBed.configureTestingModule({
        imports: [ShiftListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AdminService, useValue: service },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(ShiftListComponent);
      fixture.detectChanges();
    });

    it('deve exibir 3 linhas na tabela', () => {
      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
    });

    it('deve exibir o nome do primeiro turno', () => {
      const firstRow = fixture.nativeElement.querySelector('tbody tr:first-child');
      expect(firstRow?.textContent).toContain('Manhã');
    });
  });

  // ─── (b) Form inválido quando name está vazio ────────────────────────────────
  describe('(b) validação do formulário', () => {
    let fixture: ComponentFixture<ShiftListComponent>;
    let component: ShiftListComponent;

    beforeEach(async () => {
      const service = makeService();
      await TestBed.configureTestingModule({
        imports: [ShiftListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AdminService, useValue: service },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(ShiftListComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      // Abre o formulário
      component.openCreateForm();
      fixture.detectChanges();
    });

    it('botão Salvar deve estar desabilitado quando name está vazio', () => {
      component.form.patchValue({ name: '', startTime: '08:00', endTime: '16:00', overnight: false });
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-save"]');
      expect(btn?.disabled).toBe(true);
    });

    it('form deve ser inválido com name vazio', () => {
      component.form.patchValue({ name: '', startTime: '08:00', endTime: '16:00', overnight: false });
      expect(component.form.invalid).toBe(true);
    });
  });

  // ─── (c) Erro 422 exibe snackbar com mensagem da API ────────────────────────
  describe('(c) erro 422 exibe snackbar', () => {
    let fixture: ComponentFixture<ShiftListComponent>;
    let component: ShiftListComponent;

    beforeEach(async () => {
      const service = makeService({
        createShift: vi.fn().mockReturnValue(
          throwError(() => ({ status: 422, error: { message: 'Turno sobrepõe turno existente: Manhã' } })),
        ),
      });

      await TestBed.configureTestingModule({
        imports: [ShiftListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AdminService, useValue: service },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(ShiftListComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      component.openCreateForm();
      component.form.patchValue({ name: 'Novo', startTime: '06:00', endTime: '14:00', overnight: false });
      fixture.detectChanges();
      component.submitCreate();
      fixture.detectChanges();
    });

    it('deve exibir snackbar com mensagem da API', () => {
      const sb = fixture.nativeElement.querySelector('[data-testid="snackbar"]');
      expect(sb).toBeTruthy();
      expect(sb?.textContent).toContain('Turno sobrepõe turno existente: Manhã');
    });

    it('snackbar deve ter classe de erro', () => {
      const sb = fixture.nativeElement.querySelector('[data-testid="snackbar"]');
      expect(sb?.classList).toContain('snackbar--error');
    });
  });

  // ─── (d) Chip "Noturno" exibido apenas quando overnight=true ────────────────
  describe('(d) chip Noturno apenas para overnight=true', () => {
    let fixture: ComponentFixture<ShiftListComponent>;

    beforeEach(async () => {
      const service = makeService();
      await TestBed.configureTestingModule({
        imports: [ShiftListComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AdminService, useValue: service },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(ShiftListComponent);
      fixture.detectChanges();
    });

    it('apenas o turno noturno exibe chip Noturno', () => {
      const chips = fixture.nativeElement.querySelectorAll('.chip--night');
      expect(chips.length).toBe(1); // só o turno "Noite" é overnight
    });

    it('turnos diurnos não exibem chip noturno', () => {
      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      // rows[0] = Manhã, rows[1] = Tarde — nenhum deve ter chip noturno
      expect(rows[0]?.querySelector('.chip--night')).toBeFalsy();
      expect(rows[1]?.querySelector('.chip--night')).toBeFalsy();
    });

    it('turno overnight exibe chip noturno na linha correta', () => {
      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      // rows[2] = Noite (overnight=true)
      const nightChip = rows[2]?.querySelector('.chip--night');
      expect(nightChip).toBeTruthy();
      expect(nightChip?.textContent).toContain('Noturno');
    });
  });
});
