import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import {
  Notification,
  NotificationPage,
  NotificationService,
} from '../notification.service';
import {
  severityColor,
  formatNotificationDate,
} from '../notification.utils';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <section class="notifications-page">
      <h1 class="notifications-page__title">Histórico de Notificações</h1>

      @if (loading()) {
        <div class="loading-state" aria-label="Carregando notificações...">
          @for (i of [1,2,3,4,5]; track i) {
            <div class="skeleton-row"></div>
          }
        </div>
      } @else if (error()) {
        <div class="error-state" role="alert">{{ error() }}</div>
      } @else if (items().length === 0) {
        <div class="empty-state">Nenhuma notificação encontrada.</div>
      } @else {
        <ul class="notif-list" role="list">
          @for (n of items(); track n.id) {
            <li
              class="notif-item"
              [class.notif-item--unread]="!n.readAt"
              role="listitem"
            >
              <span
                class="notif-item__dot"
                [style.background]="severityColor(n.severity)"
                aria-hidden="true"
              ></span>
              <div class="notif-item__body">
                <p class="notif-item__title" [class.notif-item__title--bold]="!n.readAt">{{ n.title }}</p>
                <p class="notif-item__text">{{ n.body }}</p>
                <time class="notif-item__date">{{ formatDate(n.createdAt) }}</time>
              </div>
            </li>
          }
        </ul>

        @if (page() < totalPages() - 1) {
          <button class="btn-load-more" type="button" [disabled]="loadingMore()" (click)="loadMore()">
            {{ loadingMore() ? 'Carregando...' : 'Carregar mais' }}
          </button>
        }
      }
    </section>
  `,
  styles: [`
    .notifications-page {
      padding: 1.5rem 2rem;
      max-width: 720px;
      margin: 0 auto;
    }

    h1 {
      font-size: 1.25rem;
      font-weight: 700;
      color: #111827;
      margin: 0 0 1.5rem;
    }

    .skeleton-row {
      height: 4rem;
      border-radius: 0.5rem;
      background: linear-gradient(90deg, #F1F5F9 25%, #E2E8F0 50%, #F1F5F9 75%);
      background-size: 200% 100%;
      animation: shimmer 1.2s infinite;
      margin-bottom: 0.75rem;
    }

    @keyframes shimmer {
      0%   { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .error-state {
      padding: 1rem;
      background: #FEF2F2;
      border: 1px solid #FECACA;
      border-radius: 0.5rem;
      color: #991B1B;
      font-size: 0.875rem;
    }

    .empty-state {
      text-align: center;
      padding: 3rem;
      color: #94A3B8;
      font-size: 0.875rem;
    }

    .notif-list {
      list-style: none;
      padding: 0;
      margin: 0;
      border: 1px solid #E2E8F0;
      border-radius: 0.75rem;
      overflow: hidden;
    }

    .notif-item {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #F1F5F9;

      &:last-child { border-bottom: none; }
      &--unread { background: #F0F9FF; }

      &__dot {
        flex-shrink: 0;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        margin-top: 0.3rem;
      }

      &__body { flex: 1; }

      &__title {
        font-size: 0.875rem;
        color: #374151;
        margin: 0 0 0.25rem;
        &--bold { font-weight: 600; color: #111827; }
      }

      &__text {
        font-size: 0.8125rem;
        color: #6B7280;
        margin: 0 0 0.375rem;
        line-height: 1.5;
      }

      &__date {
        font-size: 0.75rem;
        color: #9CA3AF;
      }
    }

    .btn-load-more {
      display: block;
      width: 100%;
      margin-top: 1rem;
      padding: 0.625rem;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 0.5rem;
      font-size: 0.875rem;
      color: #374151;
      cursor: pointer;
      transition: background 0.15s;

      &:hover:not(:disabled) { background: #F1F5F9; }
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
  `],
})
export class NotificationsPageComponent implements OnInit {
  private readonly notificationService = inject(NotificationService);

  readonly severityColor = severityColor;
  readonly formatDate = formatNotificationDate;

  readonly items = signal<Notification[]>([]);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);

  ngOnInit(): void {
    this.loadPage(0);
  }

  loadMore(): void {
    this.loadingMore.set(true);
    this.loadPage(this.page() + 1, true);
  }

  private loadPage(pageNum: number, append = false): void {
    this.notificationService.getNotifications(pageNum).subscribe({
      next: (data: NotificationPage) => {
        this.page.set(pageNum);
        this.totalPages.set(data.totalPages);
        if (append) {
          this.items.update((prev) => [...prev, ...data.content]);
          this.loadingMore.set(false);
        } else {
          this.items.set(data.content);
          this.loading.set(false);
        }
      },
      error: () => {
        this.error.set('Erro ao carregar notificações.');
        this.loading.set(false);
        this.loadingMore.set(false);
      },
    });
  }
}
