import { TestBed } from '@angular/core/testing';
import { OfflineQueueService, OfflineNcEntry, CreateNcRequest } from './offline-queue.service';

// ─── IndexedDB mock ─────────────────────────────────────────────────────────

function buildIdbMock() {
  const store: Map<string, OfflineNcEntry> = new Map();

  const makeObjectStore = () => ({
    add: vi.fn((entry: OfflineNcEntry) => {
      const req: Record<string, unknown> = {};
      store.set(entry.id, { ...entry });
      Promise.resolve().then(() => {
        if (typeof req['onsuccess'] === 'function') (req['onsuccess'] as () => void)();
      });
      return req;
    }),
    put: vi.fn((entry: OfflineNcEntry) => {
      const req: Record<string, unknown> = {};
      store.set(entry.id, { ...entry });
      Promise.resolve().then(() => {
        if (typeof req['onsuccess'] === 'function') (req['onsuccess'] as () => void)();
      });
      return req;
    }),
    delete: vi.fn((id: string) => {
      const req: Record<string, unknown> = {};
      store.delete(id);
      Promise.resolve().then(() => {
        if (typeof req['onsuccess'] === 'function') (req['onsuccess'] as () => void)();
      });
      return req;
    }),
    getAll: vi.fn(() => {
      const req: Record<string, unknown> = {};
      Promise.resolve().then(() => {
        req['result'] = Array.from(store.values());
        if (typeof req['onsuccess'] === 'function') (req['onsuccess'] as () => void)();
      });
      return req;
    }),
    clear: vi.fn(() => {
      const req: Record<string, unknown> = {};
      store.clear();
      Promise.resolve().then(() => {
        if (typeof req['onsuccess'] === 'function') (req['onsuccess'] as () => void)();
      });
      return req;
    }),
  });

  const db = {
    objectStoreNames: { contains: () => false },
    createObjectStore: vi.fn(),
    transaction: vi.fn((_storeName: string, _mode: string) => ({
      objectStore: () => makeObjectStore(),
    })),
  };

  const mockIndexedDB = {
    open: vi.fn(() => {
      const openReq: Record<string, unknown> = {};
      Promise.resolve().then(() => {
        if (typeof openReq['onupgradeneeded'] === 'function') {
          (openReq['onupgradeneeded'] as (e: { target: unknown }) => void)({
            target: { result: db },
          });
        }
        openReq['result'] = db;
        if (typeof openReq['onsuccess'] === 'function') {
          (openReq['onsuccess'] as () => void)();
        }
      });
      return openReq;
    }),
  };

  // Install global mock
  Object.defineProperty(globalThis, 'indexedDB', {
    value: mockIndexedDB,
    writable: true,
    configurable: true,
  });

  return { store, db };
}

// ─── Tests ──────────────────────────────────────────────────────────────────

const MOCK_PAYLOAD: CreateNcRequest = {
  title: 'NC Teste Offline',
  type: 'PROCESS',
  severity: 'HIGH',
};

describe('OfflineQueueService', () => {
  let service: OfflineQueueService;
  let idbStore: Map<string, OfflineNcEntry>;

  beforeEach(() => {
    const { store } = buildIdbMock();
    idbStore = store;
    idbStore.clear();

    TestBed.configureTestingModule({});
    service = TestBed.inject(OfflineQueueService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('enqueue persiste entrada no store e atualiza pendingCount', async () => {
    await service.enqueue(MOCK_PAYLOAD);

    // Give async IDB promises time to settle
    await new Promise((r) => setTimeout(r, 50));

    expect(idbStore.size).toBeGreaterThanOrEqual(1);

    const found = Array.from(idbStore.values()).find(
      (e) => e.payload.title === MOCK_PAYLOAD.title,
    );
    expect(found).toBeDefined();
    expect(found?.payload.type).toBe('PROCESS');
  });

  it('dequeueAll retorna entradas em ordem de createdAt ASC', async () => {
    const p1: CreateNcRequest = { title: 'NC-1', type: 'PROCESS', severity: 'LOW' };
    const p2: CreateNcRequest = { title: 'NC-2', type: 'PRODUCT', severity: 'MEDIUM' };

    await service.enqueue(p1);
    // Small delay to ensure createdAt is different
    await new Promise((r) => setTimeout(r, 10));
    await service.enqueue(p2);

    await new Promise((r) => setTimeout(r, 50));

    const all = await service.dequeueAll();
    expect(all.length).toBeGreaterThanOrEqual(2);

    // Verify ascending order
    for (let i = 1; i < all.length; i++) {
      expect(all[i - 1].createdAt <= all[i].createdAt).toBe(true);
    }
  });

  it('pendingCount é atualizado após enqueue e remove', async () => {
    await service.enqueue(MOCK_PAYLOAD);
    await new Promise((r) => setTimeout(r, 50));

    const countAfterEnqueue = service.pendingCount();
    expect(countAfterEnqueue).toBeGreaterThanOrEqual(1);

    const all = await service.getAll();
    await new Promise((r) => setTimeout(r, 10));

    if (all.length > 0) {
      await service.remove(all[0].id);
      await new Promise((r) => setTimeout(r, 50));
      expect(service.pendingCount()).toBe(countAfterEnqueue - 1);
    }
  });
});
