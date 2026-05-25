import { Injectable, inject, signal } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private readonly swUpdate = inject(SwUpdate);
  readonly updateAvailable = signal(false);

  constructor() {
    if (!this.swUpdate.isEnabled) return;

    this.swUpdate.versionUpdates.pipe(takeUntilDestroyed()).subscribe((evt) => {
      if (evt.type === 'VERSION_READY') {
        this.updateAvailable.set(true);
      }
    });

    this.swUpdate.unrecoverable.pipe(takeUntilDestroyed()).subscribe(() => {
      window.location.reload();
    });
  }

  applyUpdate(): void {
    this.swUpdate.activateUpdate().then(() => window.location.reload());
  }
}
