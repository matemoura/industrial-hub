import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';
import { AuthService } from '../../auth/auth.service';
import {
  Notification,
  NotificationService,
} from '../../notifications/notification.service';
import {
  severityColor,
  formatNotificationDate,
} from '../../notifications/notification.utils';
import { MaintenanceService } from '../../maintenance/maintenance.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav.component.html',
  styleUrl: './nav.component.scss',
})
export class NavComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.authService.role;

  readonly severityColor = severityColor;
  readonly formatDate = formatNotificationDate;

  readonly unreadCount = signal<number>(0);
  readonly notifications = signal<Notification[]>([]);
  readonly panelOpen = signal(false);
  readonly panelLoading = signal(false);

  readonly belowMinCount = signal<number>(0);

  ngOnInit(): void {
    interval(60_000)
      .pipe(
        startWith(0),
        switchMap(() => this.notificationService.getUnreadCount()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (res) => this.unreadCount.set(res.count),
        error: () => { /* polling falha silenciosamente */ },
      });

    interval(300_000)
      .pipe(
        startWith(0),
        switchMap(() => this.maintenanceService.countBelowMin()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (count) => this.belowMinCount.set(count),
        error: () => { /* polling falha silenciosamente */ },
      });
  }

  togglePanel(): void {
    const willOpen = !this.panelOpen();
    this.panelOpen.set(willOpen);
    if (willOpen) {
      this.loadRecentNotifications();
    }
  }

  closePanel(): void {
    this.panelOpen.set(false);
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.unreadCount.set(0);
        this.notifications.update((list) =>
          list.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })),
        );
      },
      error: () => { /* falha silenciosa */ },
    });
  }

  markRead(notification: Notification): void {
    if (notification.readAt) return;
    this.notificationService.markRead(notification.id).subscribe({
      next: () => {
        this.unreadCount.update((n) => Math.max(0, n - 1));
        this.notifications.update((list) =>
          list.map((n) =>
            n.id === notification.id
              ? { ...n, readAt: new Date().toISOString() }
              : n,
          ),
        );
      },
      error: () => { /* falha silenciosa */ },
    });
  }

  truncate(text: string, max = 80): string {
    return text.length <= max ? text : text.slice(0, max) + '…';
  }

  logout(): void {
    this.authService.logout();
  }

  private loadRecentNotifications(): void {
    this.panelLoading.set(true);
    this.notificationService.getNotifications(0).subscribe({
      next: (page) => {
        this.notifications.set(page.content.slice(0, 10));
        this.panelLoading.set(false);
      },
      error: () => {
        this.panelLoading.set(false);
      },
    });
  }
}
