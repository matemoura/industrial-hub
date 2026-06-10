import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal, computed } from '@angular/core';
import { Subject } from 'rxjs';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { App } from './app';
import { AuthService } from './auth/auth.service';
import { I18nService } from './shared/i18n/i18n.service';
import { MaintenanceService } from './maintenance/maintenance.service';
import { OfflineQueueService } from './shared/offline/offline-queue.service';
import { OfflineSyncService } from './shared/offline/offline-sync.service';
import { NotificationService } from './notifications/notification.service';
import { PlantService } from './admin/plants/plant.service';
import { of } from 'rxjs';

function makeAuthService() {
  return {
    role: signal<string | null>('OPERATOR'),
    username: signal<string | null>('user'),
    isAuthenticated: computed(() => true),
    logout: vi.fn(),
  };
}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: makeAuthService() },
        { provide: MaintenanceService, useValue: { countBelowMin: vi.fn().mockReturnValue(of(0)) } },
        { provide: OfflineQueueService, useValue: { pendingCount: signal(0), initCount: vi.fn().mockResolvedValue(undefined) } },
        { provide: OfflineSyncService, useValue: { startSync: vi.fn(), synced$: new Subject<number>() } },
        { provide: NotificationService, useValue: { getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })), getNotifications: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })), markAllRead: vi.fn().mockReturnValue(of(undefined)), markRead: vi.fn().mockReturnValue(of(undefined)) } },
        { provide: PlantService, useValue: { listPlants: vi.fn().mockReturnValue(of([])) } },
        { provide: SwUpdate, useValue: { isEnabled: false, versionUpdates: new Subject<VersionEvent>(), unrecoverable: new Subject<{ type: string; reason: string }>(), activateUpdate: vi.fn().mockResolvedValue(true) } },
        { provide: I18nService, useValue: { currentLang: signal('pt-BR'), translate: (k: string) => k } },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should contain router-outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});
