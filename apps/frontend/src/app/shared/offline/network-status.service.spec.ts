import { TestBed } from '@angular/core/testing';
import { NetworkStatusService } from './network-status.service';

describe('NetworkStatusService', () => {
  let service: NetworkStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NetworkStatusService);
  });

  it('deve refletir o estado inicial de navigator.onLine', () => {
    // navigator.onLine é true em ambiente jsdom por padrão
    expect(service.isOnline()).toBe(navigator.onLine);
  });

  it('deve atualizar isOnline para false ao disparar evento "offline"', () => {
    window.dispatchEvent(new Event('offline'));
    expect(service.isOnline()).toBe(false);
  });

  it('deve atualizar isOnline para true ao disparar evento "online"', () => {
    window.dispatchEvent(new Event('offline'));
    expect(service.isOnline()).toBe(false);

    window.dispatchEvent(new Event('online'));
    expect(service.isOnline()).toBe(true);
  });
});
