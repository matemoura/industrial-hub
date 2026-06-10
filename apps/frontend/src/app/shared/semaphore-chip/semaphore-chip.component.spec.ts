import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SemaphoreChipComponent } from './semaphore-chip.component';

describe('SemaphoreChipComponent', () => {
  let fixture: ComponentFixture<SemaphoreChipComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SemaphoreChipComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SemaphoreChipComponent);
  });

  it('renders green chip with correct class', () => {
    fixture.componentRef.setInput('status', 'green');
    fixture.componentRef.setInput('label', 'OK');
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('.semaphore-chip');
    expect(span.classList).toContain('semaphore-chip--green');
  });

  it('renders red chip when status is red', () => {
    fixture.componentRef.setInput('status', 'red');
    fixture.componentRef.setInput('label', 'Crítico');
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('.semaphore-chip');
    expect(span.classList).toContain('semaphore-chip--red');
  });

  it('displays label text', () => {
    fixture.componentRef.setInput('status', 'amber');
    fixture.componentRef.setInput('label', 'Atenção');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('Atenção');
  });
});
