import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SlaBreachedChipComponent } from './sla-breached-chip.component';

describe('SlaBreachedChipComponent', () => {
  let fixture: ComponentFixture<SlaBreachedChipComponent>;

  function setup(slaBreached: boolean) {
    TestBed.configureTestingModule({
      imports: [SlaBreachedChipComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SlaBreachedChipComponent);
    fixture.componentRef.setInput('slaBreached', slaBreached);
    fixture.detectChanges();
  }

  it('should show chip when slaBreached = true', () => {
    setup(true);
    const chip = fixture.nativeElement.querySelector('[data-testid="sla-breached-chip"]');
    expect(chip).toBeTruthy();
    expect(chip.textContent.trim()).toBe('SLA Vencido');
  });

  it('should NOT show chip when slaBreached = false', () => {
    setup(false);
    const chip = fixture.nativeElement.querySelector('[data-testid="sla-breached-chip"]');
    expect(chip).toBeFalsy();
  });
});
