import { ComponentFixture, TestBed } from '@angular/core/testing';
import { KpiDashboardComponent } from './kpi-dashboard.component';
import { DashboardService, WidgetConfig } from '../dashboard.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

const DEFAULT_WIDGETS: WidgetConfig[] = [
  { id: 'w1', type: 'oee-avg',          column: 1, row: 1 },
  { id: 'w2', type: 'nc-open',          column: 2, row: 1 },
  { id: 'w3', type: 'nc-critical',      column: 3, row: 1 },
  { id: 'w4', type: 'wo-open',          column: 1, row: 2 },
  { id: 'w5', type: 'mttr',             column: 2, row: 2 },
  { id: 'w6', type: 'equipment-count',  column: 3, row: 2 },
];

function makeDashboardService(widgets = DEFAULT_WIDGETS): Partial<DashboardService> {
  return {
    getLayout: vi.fn().mockReturnValue(of({ widgetsJson: JSON.stringify(widgets) })),
    saveLayout: vi.fn().mockReturnValue(of({ widgetsJson: JSON.stringify(widgets) })),
    deleteLayout: vi.fn().mockReturnValue(of(undefined)),
  };
}

function makeAuthService(role = 'OPERATOR'): Partial<AuthService> {
  return {
    role: signal(role) as AuthService['role'],
  };
}

describe('KpiDashboardComponent', () => {
  let fixture: ComponentFixture<KpiDashboardComponent>;
  let component: KpiDashboardComponent;

  async function setup(
    dashSvc: Partial<DashboardService> = makeDashboardService(),
    authSvc: Partial<AuthService> = makeAuthService(),
  ) {
    await TestBed.configureTestingModule({
      imports: [KpiDashboardComponent],
      providers: [
        { provide: DashboardService, useValue: dashSvc },
        { provide: AuthService, useValue: authSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(KpiDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should render widgets from GET response', async () => {
    await setup();
    const cards = fixture.nativeElement.querySelectorAll('[data-testid^="widget-"]');
    expect(cards.length).toBe(DEFAULT_WIDGETS.length);
    expect(fixture.nativeElement.querySelector('[data-testid="widget-oee-avg"]')).toBeTruthy();
  });

  it('should activate edit mode when "Personalizar" is clicked', async () => {
    await setup();
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-personalizar"]');
    btn.click();
    fixture.detectChanges();
    expect(component.editMode()).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="btn-salvar"]')).toBeTruthy();
  });

  it('should remove widget via "×" button and show it in catalog', async () => {
    await setup();
    component.editMode.set(true);
    fixture.detectChanges();

    const removeBtns = fixture.nativeElement.querySelectorAll('[data-testid="btn-remove-widget"]');
    removeBtns[0].click();
    fixture.detectChanges();

    expect(component.widgetConfigs().length).toBe(DEFAULT_WIDGETS.length - 1);
    const catalogItem = fixture.nativeElement.querySelector('[data-testid="catalog-oee-avg"]');
    expect(catalogItem).toBeTruthy();
  });

  it('should add widget from catalog and remove it from catalog list', async () => {
    const widgets = DEFAULT_WIDGETS.slice(0, 5);
    await setup(makeDashboardService(widgets));
    component.editMode.set(true);
    fixture.detectChanges();

    const catalogBtn = fixture.nativeElement.querySelector('[data-testid="catalog-equipment-count"]');
    expect(catalogBtn).toBeTruthy();
    catalogBtn.click();
    fixture.detectChanges();

    expect(component.widgetConfigs().some((w) => w.type === 'equipment-count')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="catalog-equipment-count"]')).toBeFalsy();
  });

  it('should call PUT when saving layout and clear edit mode', async () => {
    const saveSpy = vi.fn().mockReturnValue(of({ widgetsJson: JSON.stringify(DEFAULT_WIDGETS) }));
    await setup({ ...makeDashboardService(), saveLayout: saveSpy });
    component.editMode.set(true);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="btn-salvar"]').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(saveSpy).toHaveBeenCalledWith(JSON.stringify(DEFAULT_WIDGETS));
    expect(component.editMode()).toBeFalsy();
  });

  it('should call DELETE then GET when resetting layout', async () => {
    const deleteSpy = vi.fn().mockReturnValue(of(undefined));
    let getCallCount = 0;
    const getSpy = vi.fn().mockImplementation(() => {
      getCallCount++;
      return of({ widgetsJson: JSON.stringify(DEFAULT_WIDGETS) });
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    await setup({ deleteLayout: deleteSpy, getLayout: getSpy, saveLayout: vi.fn() });
    component.editMode.set(true);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="btn-resetar"]').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(deleteSpy).toHaveBeenCalled();
    expect(getCallCount).toBeGreaterThanOrEqual(2);
    expect(component.editMode()).toBeFalsy();
  });

  it('should show load error when GET fails', async () => {
    const failingService: Partial<DashboardService> = {
      getLayout: vi.fn().mockReturnValue(throwError(() => new Error('fail'))),
    };
    await setup(failingService);
    expect(component.loadError()).toBeTruthy();
  });
});
