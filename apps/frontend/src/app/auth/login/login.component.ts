import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

interface ShowcaseStage {
  image: string;
  alt: string;
  title: string;
  sub: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  private countdownInterval?: ReturnType<typeof setInterval>;
  private showcaseInterval?: ReturnType<typeof setInterval>;

  readonly stages: ShowcaseStage[] = [
    { image: 'safira-reta.png',    alt: 'Safira Reta',    title: 'Safira · Urologia',  sub: 'Cesta ureteral reta de alta precisão' },
    { image: 'safira-curvado.png', alt: 'Safira Curvada', title: 'Safira · Curvada',   sub: 'Cesta ureteral com curvatura atraumática' },
    { image: 'safira-cobra.png',   alt: 'Safira Cobra',   title: 'Safira · Cobra',     sub: 'Design cobra para acesso difícil' },
    { image: 'twist-cut.png',      alt: 'Twist Vascular', title: 'Twist · Vascular',   sub: 'Cateter vascular de alta performance' },
  ];

  readonly stageIndex = signal(0);
  readonly prevIndex  = signal(-1);
  readonly transitioning = signal(false);

  constructor() {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  readonly loading      = signal(false);
  readonly error        = signal<string | null>(null);
  readonly blockSeconds = signal(0);
  readonly blockMessage = signal<string | null>(null);
  readonly currentYear  = new Date().getFullYear();

  readonly form = new FormGroup({
    email:    new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    this.startShowcase();
  }

  goToStage(index: number): void {
    if (index === this.stageIndex()) return;
    this.prevIndex.set(this.stageIndex());
    this.transitioning.set(true);
    this.stageIndex.set(index);
    this.restartShowcase();
    setTimeout(() => this.transitioning.set(false), 800);
  }

  private startShowcase(): void {
    this.showcaseInterval = setInterval(() => {
      const next = (this.stageIndex() + 1) % this.stages.length;
      this.prevIndex.set(this.stageIndex());
      this.transitioning.set(true);
      this.stageIndex.set(next);
      setTimeout(() => this.transitioning.set(false), 800);
    }, 3400);
  }

  private restartShowcase(): void {
    clearInterval(this.showcaseInterval);
    this.startShowcase();
  }

  submit(): void {
    if (this.form.invalid || this.loading() || this.blockSeconds() > 0) return;
    this.loading.set(true);
    this.error.set(null);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => { this.loading.set(false); this.router.navigate(['/dashboard']); },
      error: (err: { status?: number; headers?: { get: (h: string) => string | null }; error?: { message?: string } }) => {
        this.loading.set(false);
        if (err.status === 429) {
          const retryAfter = Number(err.headers?.get('Retry-After')) || 300;
          this.startCountdown(retryAfter);
        } else {
          this.error.set(err?.error?.message ?? 'Erro ao conectar. Tente novamente.');
        }
      },
    });
  }

  private startCountdown(seconds: number): void {
    clearInterval(this.countdownInterval);
    this.blockSeconds.set(seconds);
    this.blockMessage.set(`Muitas tentativas de login. Aguarde ${seconds} segundos.`);

    this.countdownInterval = setInterval(() => {
      const remaining = this.blockSeconds() - 1;
      if (remaining <= 0) {
        clearInterval(this.countdownInterval);
        this.countdownInterval = undefined;
        this.blockSeconds.set(0);
        this.blockMessage.set(null);
      } else {
        this.blockSeconds.set(remaining);
        this.blockMessage.set(`Muitas tentativas de login. Aguarde ${remaining} segundos.`);
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.countdownInterval);
    clearInterval(this.showcaseInterval);
  }
}
