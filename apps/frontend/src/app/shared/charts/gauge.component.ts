import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-gauge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="gauge" [style.width.px]="size()" [attr.aria-label]="sub() + ': ' + displayValue()">
      <svg [attr.width]="size()" [attr.height]="svgH()" [attr.viewBox]="vb()">
        <!-- track -->
        <path [attr.d]="trackPath()" fill="none" stroke="#D9E4E8" [attr.stroke-width]="sw()" stroke-linecap="round"/>
        <!-- value arc -->
        @if (value() > 0) {
          <path [attr.d]="valuePath()" fill="none" [attr.stroke]="color()" [attr.stroke-width]="sw()"
                stroke-linecap="round" class="gauge-arc"/>
        }
        <!-- target tick -->
        @if (target() != null) {
          <line [attr.x1]="tick().x1" [attr.y1]="tick().y1"
                [attr.x2]="tick().x2" [attr.y2]="tick().y2"
                stroke="#1F3A4A" stroke-width="2.5" stroke-linecap="round"/>
        }
      </svg>
      <div class="gauge__label">
        <div class="gauge__value" [style.color]="color()">{{ displayValue() }}</div>
        @if (sub()) { <div class="gauge__sub">{{ sub() }}</div> }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .gauge { position: relative; }
    svg { display: block; }
    .gauge__label {
      position: absolute; left: 0; right: 0; bottom: 0;
      text-align: center; padding-bottom: 4px;
    }
    .gauge__value { font-weight: 700; font-variant-numeric: tabular-nums; line-height: 1; font-size: inherit; }
    .gauge__sub {
      font-size: 0.62em; font-weight: 600; letter-spacing: .14em;
      text-transform: uppercase; color: #818286; margin-top: 3px;
    }
    .gauge-arc { transition: stroke-dasharray 0.9s cubic-bezier(.2,.7,.2,1); }
  `],
})
export class GaugeComponent {
  readonly value  = input.required<number>();      // 0–1
  readonly target = input<number | null>(null);    // 0–1
  readonly color  = input<string>('#56A4BB');
  readonly size   = input<number>(140);
  readonly sub    = input<string>('');
  readonly label  = input<string>('');             // override display text

  readonly sw     = computed(() => Math.max(8, Math.round(this.size() * 0.072)));
  readonly svgH   = computed(() => Math.round(this.size() / 1.48));
  readonly vb     = computed(() => `0 0 ${this.size()} ${this.svgH()}`);

  // geometry helpers
  private cx()  { return this.size() / 2; }
  private cy()  { return this.size() / 2 + Math.round(this.size() * 0.06); }
  private r()   { return this.size() / 2 - this.sw() / 2 - 2; }

  readonly trackPath = computed(() => {
    const cx = this.cx(), cy = this.cy(), r = this.r();
    const x1 = cx + r * Math.cos(Math.PI);
    const y1 = cy + r * Math.sin(Math.PI);
    const x2 = cx + r * Math.cos(2 * Math.PI);
    const y2 = cy + r * Math.sin(2 * Math.PI);
    return `M ${f(x1)} ${f(y1)} A ${f(r)} ${f(r)} 0 1 1 ${f(x2)} ${f(y2)}`;
  });

  readonly valuePath = computed(() => {
    const v = Math.max(0.001, Math.min(1, this.value()));
    const cx = this.cx(), cy = this.cy(), r = this.r();
    const a0 = Math.PI, a1 = Math.PI + Math.PI * v;
    const x1 = cx + r * Math.cos(a0), y1 = cy + r * Math.sin(a0);
    const x2 = cx + r * Math.cos(a1), y2 = cy + r * Math.sin(a1);
    const large = a1 - a0 > Math.PI ? 1 : 0;
    return `M ${f(x1)} ${f(y1)} A ${f(r)} ${f(r)} 0 ${large} 1 ${f(x2)} ${f(y2)}`;
  });

  readonly tick = computed(() => {
    const t = this.target();
    if (t == null) return { x1: '0', y1: '0', x2: '0', y2: '0' };
    const cx = this.cx(), cy = this.cy(), r = this.r(), sw = this.sw();
    const a = Math.PI + Math.PI * t;
    return {
      x1: f(cx + (r - sw / 2) * Math.cos(a)),
      y1: f(cy + (r - sw / 2) * Math.sin(a)),
      x2: f(cx + (r + sw / 2) * Math.cos(a)),
      y2: f(cy + (r + sw / 2) * Math.sin(a)),
    };
  });

  readonly displayValue = computed(() => {
    if (this.label()) return this.label();
    return (this.value() * 100).toFixed(1) + '%';
  });
}

function f(n: number): string { return n.toFixed(2); }
