import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { NetworkStatusService } from './network-status.service';
import { OfflineQueueService } from './offline-queue.service';
import { QmsService } from '../../qms/qms.service';

const MAX_ATTEMPTS = 3;

export interface SyncResult {
  synced: number;
  failed: number;
}

@Injectable({ providedIn: 'root' })
export class OfflineSyncService {
  private readonly networkStatus = inject(NetworkStatusService);
  private readonly offlineQueue = inject(OfflineQueueService);
  private readonly qmsService = inject(QmsService);

  readonly synced$ = new Subject<SyncResult>();
  readonly lastSyncAt = signal<Date | null>(null);

  private syncing = false;

  startSync(): void {
    // Watch for online transitions
    let previousOnline = this.networkStatus.isOnline();

    // Poll the signal via a simple effect-like mechanism using interval
    const checkInterval = setInterval(() => {
      const nowOnline = this.networkStatus.isOnline();
      if (!previousOnline && nowOnline) {
        void this.drainQueue();
      }
      previousOnline = nowOnline;
    }, 2000);

    // Also drain immediately if already online on startup
    if (previousOnline) {
      void this.drainQueue();
    }

    // Store handle for potential cleanup (not strictly needed with providedIn: root)
    return void checkInterval;
  }

  async drainQueue(): Promise<void> {
    if (this.syncing) return;
    this.syncing = true;

    try {
      const entries = await this.offlineQueue.dequeueAll();
      if (entries.length === 0) {
        this.syncing = false;
        return;
      }

      let syncedCount = 0;
      let failedCount = 0;

      for (const entry of entries) {
        try {
          await new Promise<void>((resolve, reject) => {
            this.qmsService.createNc(entry.payload).subscribe({
              next: () => resolve(),
              error: (err) => reject(err),
            });
          });
          await this.offlineQueue.remove(entry.id);
          syncedCount++;
        } catch {
          entry.attempts++;
          if (entry.attempts >= MAX_ATTEMPTS) {
            await this.offlineQueue.remove(entry.id);
            failedCount++;
            console.warn(`NC '${entry.payload.title}' falhou após 3 tentativas — descartada`);
          } else {
            await this.offlineQueue.updateAttempts(entry);
          }
        }
      }

      if (syncedCount > 0 || failedCount > 0) {
        this.synced$.next({ synced: syncedCount, failed: failedCount });
        this.lastSyncAt.set(new Date());
      }
    } finally {
      this.syncing = false;
    }
  }
}
