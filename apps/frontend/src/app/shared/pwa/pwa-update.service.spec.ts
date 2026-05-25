import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { PwaUpdateService } from './pwa-update.service';
import { SwUpdate, VersionEvent } from '@angular/service-worker';

function makeSwUpdate(isEnabled = true) {
  return {
    isEnabled,
    versionUpdates: new Subject<VersionEvent>(),
    unrecoverable: new Subject<{ type: string; reason: string }>(),
    activateUpdate: vi.fn().mockResolvedValue(true),
  };
}

describe('PwaUpdateService', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('updateAvailable permanece false quando SwUpdate não está habilitado', () => {
    const swUpdate = makeSwUpdate(false);
    TestBed.configureTestingModule({
      providers: [
        PwaUpdateService,
        { provide: SwUpdate, useValue: swUpdate },
      ],
    });
    const service = TestBed.inject(PwaUpdateService);
    expect(service.updateAvailable()).toBe(false);
  });

  it('updateAvailable fica true quando VERSION_READY emitido', () => {
    const swUpdate = makeSwUpdate(true);
    TestBed.configureTestingModule({
      providers: [
        PwaUpdateService,
        { provide: SwUpdate, useValue: swUpdate },
      ],
    });
    const service = TestBed.inject(PwaUpdateService);

    swUpdate.versionUpdates.next({ type: 'VERSION_READY' } as VersionEvent);

    expect(service.updateAvailable()).toBe(true);
  });
});
