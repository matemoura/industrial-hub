import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router, RouterLink, NavigationEnd } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { interval, timer } from 'rxjs';
import { filter, map, startWith, switchMap } from 'rxjs/operators';
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
import { PlantService, PlantResponse } from '../../admin/plants/plant.service';
import { PlantSelectorComponent, PlantOption } from '../plant-selector/plant-selector.component';
import { PlantContextService } from '../plant-selector/plant-context.service';
import { ShellStateService } from '../shell/shell-state.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PlantSelectorComponent],
  templateUrl: './nav.component.html',
  styleUrl: './nav.component.scss',
})
export class NavComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly plantService = inject(PlantService);
  private readonly plantContextService = inject(PlantContextService);
  private readonly destroyRef = inject(DestroyRef);
  readonly shellState = inject(ShellStateService);
  private readonly router = inject(Router);
  readonly pwaUpdate = inject(PwaUpdateService);
  readonly pwaInstall = inject(PwaInstallService);
  readonly offlineQueue = inject(OfflineQueueService);
  private readonly offlineSync = inject(OfflineSyncService);

  readonly role = this.authService.role;

  readonly userInitials = computed(() => {
    const u = this.authService.username() ?? '';
    const parts = u.split(/[.\-_\s]/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return u.slice(0, 2).toUpperCase();
  });

  readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => this.routeToTitle(e.urlAfterRedirects)),
      startWith(this.routeToTitle(this.router.url)),
    ),
    { initialValue: '' },
  );

  readonly severityColor = severityColor;
  readonly formatDate = formatNotificationDate;

  readonly unreadCount = signal<number>(0);
  readonly notifications = signal<Notification[]>([]);
  readonly panelOpen = signal(false);
  readonly panelLoading = signal(false);

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

  private routeToTitle(url: string): string {
    const map: Record<string, string> = {
      '/dashboard': 'Dashboard',
      '/oee/efficiency': 'Eficiência OEE',
      '/oee/planned-downtimes': 'Paradas Planejadas',
      '/indirect-activities': 'Atividades Indiretas',
      '/processes': 'Processos',
      '/summary': 'Resumo',
      '/qms/non-conformances': 'Não-Conformidades',
      '/maintenance/equipment': 'Manutenção',
      '/production/products': 'Produção',
      '/production/tracking': 'Acompanhamento',
      '/production/sterilization-loads': 'Cargas',
      '/production/planning': 'Planejamento',
      '/production/overview': 'Visão Geral',
      '/production/reports': 'Relatórios',
      '/analytics/oee': 'Analytics OEE',
      '/admin/users': 'Usuários',
      '/admin/alert-thresholds': 'Alertas',
      '/admin/shifts': 'Turnos',
      '/admin/sla-rules': 'SLA',
      '/admin/plants': 'Plantas',
      '/admin/lgpd': 'LGPD',
      '/admin/webhooks': 'Webhooks',
      '/change-password': 'Alterar Senha',
      '/privacy/export': 'Exportar meus dados',
      '/notifications': 'Notificações',
      '/training/courses': 'Catálogo de Cursos',
      '/training/records/me': 'Meus Treinamentos',
      '/training/records': 'Registros de Treinamentos',
      '/training/competency-matrix': 'Matriz de Competências',
      '/training/dashboard': 'Dashboard de Treinamentos',
      '/maintenance/calibration/dashboard': 'Dashboard de Calibração',
      '/maintenance/calibration': 'Agenda de Calibrações',
      '/changes': 'Controle de Mudanças',
    };
    const normalized = url.split('?')[0];
    const match = Object.keys(map)
      .sort((a, b) => b.length - a.length)
      .find((k) => normalized.startsWith(k));
    return match ? map[match] : '';
  }
}
