import {
  ChangeDetectionStrategy, Component, DestroyRef,
  OnInit, inject, signal, computed,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { filter, startWith, switchMap } from 'rxjs/operators';
import { AuthService } from '../../auth/auth.service';
import { MaintenanceService } from '../../maintenance/maintenance.service';
import { OfflineQueueService } from '../offline/offline-queue.service';
import { ShellStateService } from './shell-state.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  readonly offlineQueue = inject(OfflineQueueService);
  readonly shellState = inject(ShellStateService);

  readonly role = this.authService.role;
  readonly username = this.authService.username;

  readonly initials = computed(() => {
    const u = this.username() ?? '';
    const parts = u.split(/[.\-_\s]/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return u.slice(0, 2).toUpperCase();
  });

  readonly belowMinCount = signal<number>(0);

  ngOnInit(): void {
    // Fechar sidebar mobile ao navegar
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.shellState.close());

    interval(300_000)
      .pipe(
        startWith(0),
        filter(() => this.authService.isAuthenticated()),
        switchMap(() => this.maintenanceService.countBelowMin()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: (c) => this.belowMinCount.set(c), error: () => {} });
  }
}
