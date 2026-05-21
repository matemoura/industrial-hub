import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'msb_plant_id';

@Injectable({ providedIn: 'root' })
export class PlantContextService {
  readonly selectedPlantId = signal<string | null>(this.loadFromStorage());

  setPlant(id: string | null): void {
    this.selectedPlantId.set(id);
    if (id) {
      localStorage.setItem(STORAGE_KEY, id);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  getHeader(): Record<string, string> {
    const id = this.selectedPlantId();
    return id ? { 'X-Plant-Id': id } : {};
  }

  private loadFromStorage(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
