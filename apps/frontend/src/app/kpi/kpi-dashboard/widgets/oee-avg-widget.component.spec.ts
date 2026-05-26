import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OeeAvgWidgetComponent } from './oee-avg-widget.component';
import { KpiService } from '../../kpi.service';

function makeKpiService(fail = false, oeeValue: number | null = 0.72) {
  return {
    getSummary: vi.fn().mockReturnValue(
      fail
        ? throwError(() => new Error('HTTP 500'))
        : of({
            oeeAvgLast30Days: oeeValue,
            totalNcOpen: 0,
            totalNcCritical: 0,
            totalWorkOrdersOpen: 0,
            mttrGlobalHours: null,
            activeEquipmentCount: 0,
          }),
    ),
  };
}

async function setup(fail = false, oeeValue: number | null = 0.72): Promise<{
  fixture: ComponentFixture<OeeAvgWidgetComponent>;
  component: OeeAvgWidgetComponent;
}> {
  const kpiService = makeKpiService(fail, oeeValue);

  TestBed.configureTestingModule({
    imports: [OeeAvgWidgetComponent],
    providers: [{ provide: KpiService, useValue: kpiService }],
  }).compileComponents();

  const fixture = TestBed.createComponent(OeeAvgWidgetComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, component };
}

describe('OeeAvgWidgetComponent — US-078 AC#13', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('exibe .kpi-value com o valor formatado quando getSummary() retorna dados', async () => {
    const { fixture } = await setup(false, 0.72);
    const valueEl: HTMLElement | null = fixture.nativeElement.querySelector('.kpi-value');
    expect(valueEl).toBeTruthy();
    expect(valueEl!.textContent?.trim()).toBe('72.0%');
  });

  it('exibe .widget-error quando getSummary() retorna erro, sem afetar outros widgets', async () => {
    // Arrange / Act
    let threw = false;
    try {
      const { fixture } = await setup(true);
      const errorEl: HTMLElement | null = fixture.nativeElement.querySelector('.widget-error');
      expect(errorEl).toBeTruthy();

      // The success block must NOT be rendered
      const valueEl = fixture.nativeElement.querySelector('.kpi-value');
      expect(valueEl).toBeNull();
    } catch {
      threw = true;
    }
    // Verifies the component handles the error gracefully without propagating it
    expect(threw).toBeFalsy();
  });
});
