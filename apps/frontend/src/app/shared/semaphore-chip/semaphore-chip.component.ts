import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-semaphore-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './semaphore-chip.component.html',
  styleUrl: './semaphore-chip.component.scss',
})
export class SemaphoreChipComponent {
  status = input<'green' | 'amber' | 'red'>('green');
  label = input<string>('');
}
