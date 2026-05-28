import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, timer } from 'rxjs';
import { filter, startWith, switchMap } from 'rxjs/operators';
import { AuthService } from '../../auth/auth.service';
import {
  Notification,
  NotificationService,
} from '../../notifications/notification.service';
import { PwaUpdateService } from '../pwa/pwa-update.service';
import { PwaInstallService } from '../pwa/pwa-install.service';
import { OfflineQueueService } from '../offline/offline-queue.service';
import { OfflineSyncService, SyncResult } from '../offline/offline-sync.service';
import {
  severityColor,
  formatNotificationDate,
} from '../../notifications/notification.utils';
import { MaintenanceService } from '../../maintenance/maintenance.service';
import { PlantService, PlantResponse } from '../../admin/plants/plant.service';
import { PlantSelectorComponent, PlantOption } from '../plant-selector/plant-selector.component';
import { PlantContextService } from '../plant-selector/plant-context.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, PlantSelectorComponent],
  templateUrl: './nav.component.html',
  styleUrl: './nav.component.scss',
})
export class NavComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly plantService = inject(PlantService);
  private readonly plantContextService = inject(PlantContextService);
  private readonly destroyRef = inject(DestroyRef);
  readonly pwaUpdate = inject(PwaUpdateService);
  readonly pwaInstall = inject(PwaInstallService);
  readonly offlineQueue = inject(OfflineQueueService);
  private readonly offlineSync = inject(OfflineSyncService);

  readonly role = this.authService.role;

  readonly severityColor = severityColor;
  readonly formatDate = formatNotificationDate;

  readonly unreadCount = signal<number>(0);
  readonly notifications = signal<Notification[]>([]);
  readonly panelOpen = signal(false);
  readonly panelLoading = signal(false);

  readonly belowMinCount = signal<number>(0);
  readonly syncMsg = signal<string | null>(null);

  readonly userPlants = signal<PlantResponse[]>([]);
  readonly plantOptions = computed<PlantOption[]>(() =>
    this.userPlants().map((p) => ({ id: p.id, name: p.name, code: p.code })),
  );

  ngOnInit(): void {
    void this.offlineQueue.initCount();
    this.offlineSync.startSync();

    this.offlineSync.synced$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result: SyncResult) => {
        const msg = result.failed > 0
          ? `${result.synced} NC(s) sincronizada(s); ${result.failed} falharam após 3 tentativas`
          : `${result.synced} NC(s) sincronizada(s) com sucesso`;
        const duration = result.failed > 0 ? 8000 : 5000;
        this.syncMsg.set(msg);
        timer(duration)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => this.syncMsg.set(null));
      });

    interval(60_000)
      .pipe(
        startWith(0),
        filter(() => this.authService.isAuthenticated()),
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
        filter(() => this.authService.isAuthenticated()),
        switchMap(() => this.maintenanceService.countBelowMin()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (count) => this.belowMinCount.set(count),
        error: () => { /* polling falha silenciosamente */ },
      });

    if (this.authService.isAuthenticated()) {
      this.loadUserPlants();
    }
  }

  private loadUserPlants(): void {
    const role = this.role();
    if (role === 'ADMIN') {
      this.plantService.listPlants()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (plants) => {
            this.userPlants.set(plants);
            // Invalida seleção stale: se o UUID salvo não corresponde a uma planta ativa
            const storedId = this.plantContextService.selectedPlantId();
            if (storedId && !plants.find((p) => p.id === storedId)) {
              this.plantContextService.setPlant(null);
            }
          },
          error: () => { /* falha silenciosa — não impede nav */ },
        });
    }
    // OPERATOR/SUPERVISOR: carregar via endpoint de plantas do usuário quando disponível
    // Por ora ADMIN já carrega todas as plantas para o seletor
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
