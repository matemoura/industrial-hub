import { Injectable, signal } from '@angular/core';
import { NcType, NcSeverity } from '../../qms/qms.service';

export interface CreateNcRequest {
  title: string;
  type: NcType;
  severity: NcSeverity;
  description?: string;
  supplierId?: string;
}

export interface OfflineNcEntry {
  id: string;
  payload: CreateNcRequest;
  createdAt: string;
  attempts: number;
}

@Injectable({ providedIn: 'root' })
export class OfflineQueueService {
  private readonly DB_NAME = 'industrial-hub-offline';
  private readonly STORE = 'offline_ncs';
  private readonly DB_VERSION = 1;

  readonly pendingCount = signal(0);

  private openDb(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.DB_NAME, this.DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains(this.STORE)) {
          db.createObjectStore(this.STORE, { keyPath: 'id' });
        }
      };

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async enqueue(payload: CreateNcRequest): Promise<void> {
    const entry: OfflineNcEntry = {
      id: crypto.randomUUID(),
      payload,
      createdAt: new Date().toISOString(),
      attempts: 0,
    };

    const db = await this.openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(this.STORE, 'readwrite');
      const store = tx.objectStore(this.STORE);
      const req = store.add(entry);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });

    await this.refreshCount();
  }

  async getAll(): Promise<OfflineNcEntry[]> {
    const db = await this.openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(this.STORE, 'readonly');
      const store = tx.objectStore(this.STORE);
      const req = store.getAll();
      req.onsuccess = () => {
        const entries = (req.result as OfflineNcEntry[]).sort((a, b) =>
          a.createdAt.localeCompare(b.createdAt),
        );
        resolve(entries);
      };
      req.onerror = () => reject(req.error);
    });
  }

  async dequeueAll(): Promise<OfflineNcEntry[]> {
    return this.getAll();
  }

  async remove(id: string): Promise<void> {
    const db = await this.openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(this.STORE, 'readwrite');
      const store = tx.objectStore(this.STORE);
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });

    await this.refreshCount();
  }

  async updateAttempts(entry: OfflineNcEntry): Promise<void> {
    const db = await this.openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(this.STORE, 'readwrite');
      const store = tx.objectStore(this.STORE);
      const req = store.put(entry);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async clearAll(): Promise<void> {
    const db = await this.openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(this.STORE, 'readwrite');
      const store = tx.objectStore(this.STORE);
      const req = store.clear();
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });

    this.pendingCount.set(0);
  }

  private async refreshCount(): Promise<void> {
    const entries = await this.getAll();
    this.pendingCount.set(entries.length);
  }

  async initCount(): Promise<void> {
    await this.refreshCount();
  }
}
