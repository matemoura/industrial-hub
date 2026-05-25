import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private deferredPrompt: any = null;
  readonly canInstall = signal(false);

  constructor() {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.deferredPrompt = e;
      this.canInstall.set(true);
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canInstall.set(false);
    });
  }

  install(): void {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (this.deferredPrompt as any).userChoice.then(() => {
      this.deferredPrompt = null;
      this.canInstall.set(false);
    });
  }
}
