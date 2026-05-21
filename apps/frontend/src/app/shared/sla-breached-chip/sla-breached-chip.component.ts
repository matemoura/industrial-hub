import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-sla-breached-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (slaBreached()) {
      <span class="sla-breached-chip" role="status" aria-label="SLA vencido" data-testid="sla-breached-chip">
        SLA Vencido
      </span>
    }
  `,
  styles: [`
    .sla-breached-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      background: #FEE2E2;
      color: #991B1B;
      border: 1px solid #FECACA;
      animation: sla-pulse 2s ease-in-out infinite;
    }

    @keyframes sla-pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: 0.6; }
    }
  `],
})
export class SlaBreachedChipComponent {
  readonly slaBreached = input.required<boolean>();
}
