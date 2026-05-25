import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OfflineSyncService } from './offline-sync.service';
import { NetworkStatusService } from './network-status.service';
import { OfflineQueueService, OfflineNcEntry, CreateNcRequest } from './offline-queue.service';
import { QmsService } from '../../qms/qms.service';

const MOCK_ENTRY: OfflineNcEntry = {
  id: 'offline-nc-1',
  payload: { title: 'Teste offline', type: 'PROCESS', severity: 'LOW' } as CreateNcRequest,
  createdAt: new Date().toISOString(),
  attempts: 0,
};

function makeQmsService(fail = false) {
  return {
    createNc: vi.fn().mockReturnValue(
      fail
        ? throwError(() => new Error('server error'))
        : of({ id: 'nc-1' }),
    ),
  };
}

function makeQueueService(entries: OfflineNcEntry[] = []) {
  return {
    dequeueAll: vi.fn().mockResolvedValue(entries),
    remove: vi.fn().mockResolvedValue(undefined),
    updateAttempts: vi.fn().mockResolvedValue(undefined),
    pendingCount: vi.fn().mockReturnValue(entries.length),
    initCount: vi.fn().mockResolvedValue(undefined),
  };
}

describe('OfflineSyncService', () => {
  let service: OfflineSyncService;

  afterEach(() => TestBed.resetTestingModule());

  it('drainQueue é chamado ao transitar para online', async () => {
    const networkStatus = new NetworkStatusService();
    const queueService = makeQueueService([MOCK_ENTRY]);
    const qmsService = makeQmsService();

    await TestBed.configureTestingModule({
      providers: [
        OfflineSyncService,
        { provide: NetworkStatusService, useValue: networkStatus },
        { provide: OfflineQueueService, useValue: queueService },
        { provide: QmsService, useValue: qmsService },
      ],
    }).compileComponents();

    service = TestBed.inject(OfflineSyncService);

    // Simulate offline then online
    networkStatus.isOnline.set(false);
    service.startSync();

    networkStatus.isOnline.set(true);

    // Wait for async polling cycle
    await new Promise((r) => setTimeout(r, 2500));

    expect(queueService.dequeueAll).toHaveBeenCalled();
  });

  it('remove é chamado após sync bem-sucedido; após 3 falhas a entrada é descartada', async () => {
    const failingEntry: OfflineNcEntry = { ...MOCK_ENTRY, id: 'fail-nc', attempts: 2 };
    const networkStatus = new NetworkStatusService();
    networkStatus.isOnline.set(true);

    const queueService = makeQueueService([failingEntry]);
    const qmsService = makeQmsService(true); // always fails

    await TestBed.configureTestingModule({
      providers: [
        OfflineSyncService,
        { provide: NetworkStatusService, useValue: networkStatus },
        { provide: OfflineQueueService, useValue: queueService },
        { provide: QmsService, useValue: qmsService },
      ],
    }).compileComponents();

    service = TestBed.inject(OfflineSyncService);

    await service.drainQueue();

    // With 2 previous attempts + 1 now = 3 => should be removed (discarded)
    expect(queueService.remove).toHaveBeenCalledWith(failingEntry.id);
    expect(queueService.updateAttempts).not.toHaveBeenCalled();
  });
});
