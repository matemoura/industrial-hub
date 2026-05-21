import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { signal, computed } from '@angular/core';
import { NavComponent } from './nav.component';
import { AuthService } from '../../auth/auth.service';
import { NotificationService, Notification } from '../../notifications/notification.service';
import { PlantService } from '../../admin/plants/plant.service';

const MOCK_NOTIFICATIONS: Notification[] = [
  {
    id: 'notif-001',
    username: 'supervisor1',
    title: 'OEE abaixo do limiar',
    body: 'OEE médio dos últimos 7 dias ficou abaixo de 65%',
    severity: 'WARNING',
    createdAt: '2026-05-20T08:00:00',
    readAt: null,
  },
  {
    id: 'notif-002',
    username: null,
    title: 'NC crítica aberta',
    body: 'Nova não-conformidade crítica registrada',
    severity: 'CRITICAL',
    createdAt: '2026-05-20T09:00:00',
    readAt: '2026-05-20T09:30:00',
  },
];

function makeAuthService(role: string) {
  const roleSignal = signal(role);
  return {
    role: roleSignal,
    isAuthenticated: computed(() => true),
    logout: vi.fn(),
  };
}

function makePlantService() {
  return {
    listPlants: vi.fn().mockReturnValue(of([])),
    getUserPlants: vi.fn().mockReturnValue(of([])),
  };
}

function makeNotificationService(unreadCount = 3, notifications = MOCK_NOTIFICATIONS) {
  return {
    getUnreadCount: vi.fn().mockReturnValue(of({ count: unreadCount })),
    getNotifications: vi.fn().mockReturnValue(
      of({ content: notifications, page: 0, size: 20, totalElements: notifications.length, totalPages: 1 }),
    ),
    markRead: vi.fn().mockReturnValue(of(undefined)),
    markAllRead: vi.fn().mockReturnValue(of(undefined)),
  };
}

describe('NavComponent', () => {
  let fixture: ComponentFixture<NavComponent>;
  let component: NavComponent;
  let notificationService: ReturnType<typeof makeNotificationService>;

  function setup(role = 'OPERATOR', unreadCount = 3) {
    notificationService = makeNotificationService(unreadCount);
    TestBed.configureTestingModule({
      imports: [NavComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: makeAuthService(role) },
        { provide: NotificationService, useValue: notificationService },
        { provide: PlantService, useValue: makePlantService() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-1 — sino e badge de notificação', () => {
    it('should render notification bell button', () => {
      setup();
      const bell = fixture.nativeElement.querySelector('[data-testid="notification-bell"]');
      expect(bell).toBeTruthy();
    });

    it('badge deve ser visível quando unreadCount > 0', () => {
      setup('OPERATOR', 5);
      // O serviço emite imediatamente via of(), então o signal já está atualizado
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="notification-badge"]');
      expect(badge).toBeTruthy();
      expect(badge.textContent.trim()).toBe('5');
    });

    it('badge deve estar oculto quando unreadCount = 0', () => {
      setup('OPERATOR', 0);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="notification-badge"]');
      expect(badge).toBeFalsy();
    });

    it('badge deve exibir "99+" quando unreadCount > 99', () => {
      setup('OPERATOR', 150);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="notification-badge"]');
      expect(badge?.textContent.trim()).toBe('99+');
    });
  });

  describe('AC-2 — painel de notificações', () => {
    it('should open panel on bell click', () => {
      setup();
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="notification-bell"]').click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="notification-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should close panel on second bell click', () => {
      setup();
      fixture.detectChanges();
      const bell = fixture.nativeElement.querySelector('[data-testid="notification-bell"]');
      bell.click();
      fixture.detectChanges();
      bell.click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="notification-panel"]');
      expect(panel).toBeFalsy();
    });
  });

  describe('AC-3 — "Marcar todas como lidas"', () => {
    it('deve chamar markAllRead() e zerar o badge', () => {
      setup('OPERATOR', 3);
      fixture.detectChanges();
      component.markAllRead();
      expect(notificationService.markAllRead).toHaveBeenCalledOnce();
      expect(component.unreadCount()).toBe(0);
    });

    it('badge deve desaparecer após markAllRead', () => {
      setup('OPERATOR', 3);
      fixture.detectChanges();
      component.markAllRead();
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="notification-badge"]');
      expect(badge).toBeFalsy();
    });
  });

  describe('AC-4 — markRead individual', () => {
    it('deve chamar markRead e decrementar unreadCount', () => {
      setup('OPERATOR', 3);
      fixture.detectChanges();
      component.unreadCount.set(3);
      component.markRead(MOCK_NOTIFICATIONS[0]); // readAt = null
      expect(notificationService.markRead).toHaveBeenCalledWith('notif-001');
      expect(component.unreadCount()).toBe(2);
    });

    it('não deve chamar markRead se notificação já foi lida', () => {
      setup('OPERATOR', 0);
      fixture.detectChanges();
      component.markRead(MOCK_NOTIFICATIONS[1]); // readAt já preenchido
      expect(notificationService.markRead).not.toHaveBeenCalled();
    });

    it('unreadCount não deve cair abaixo de 0', () => {
      setup('OPERATOR', 0);
      fixture.detectChanges();
      component.unreadCount.set(0);
      component.markRead(MOCK_NOTIFICATIONS[0]);
      expect(component.unreadCount()).toBe(0);
    });
  });

  describe('AC-5 — polling silencioso em caso de erro', () => {
    it('não deve lançar erro quando getUnreadCount falha', () => {
      notificationService = {
        getUnreadCount: vi.fn().mockReturnValue(throwError(() => new Error('Network'))),
        getNotifications: vi.fn().mockReturnValue(
          of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
        ),
        markRead: vi.fn(),
        markAllRead: vi.fn(),
      };
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [NavComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: AuthService, useValue: makeAuthService('OPERATOR') },
          { provide: NotificationService, useValue: notificationService },
          { provide: PlantService, useValue: makePlantService() },
        ],
      }).compileComponents();
      // Should not throw
      expect(() => {
        fixture = TestBed.createComponent(NavComponent);
        fixture.detectChanges();
      }).not.toThrow();
    });
  });

  describe('AC-6 — helpers', () => {
    it('truncate deve cortar texto acima de 80 chars', () => {
      setup();
      const text = 'a'.repeat(100);
      const result = component.truncate(text);
      expect(result.length).toBeLessThanOrEqual(83); // 80 + '…'
      expect(result.endsWith('…')).toBe(true);
    });

    it('truncate deve retornar texto inteiro se <= 80 chars', () => {
      setup();
      const text = 'hello world';
      expect(component.truncate(text)).toBe(text);
    });

    it('severityColor deve retornar vermelho para CRITICAL', () => {
      setup();
      expect(component.severityColor('CRITICAL')).toBe('#EF4444');
    });

    it('severityColor deve retornar laranja para WARNING', () => {
      setup();
      expect(component.severityColor('WARNING')).toBe('#F97316');
    });

    it('severityColor deve retornar teal para INFO', () => {
      setup();
      expect(component.severityColor('INFO')).toBe('#0099B8');
    });
  });

  describe('AC-7 — visibilidade de links por role', () => {
    function getLinkTexts(f: ComponentFixture<NavComponent>): string[] {
      return Array.from(f.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>)
        .map((l) => l.textContent?.trim() ?? '');
    }

    it('OPERATOR não deve ver link Usuários', () => {
      setup('OPERATOR');
      expect(getLinkTexts(fixture)).not.toContain('Usuários');
    });

    it('ADMIN deve ver link Usuários', () => {
      setup('ADMIN');
      fixture.detectChanges();
      expect(getLinkTexts(fixture)).toContain('Usuários');
    });

    it('ADMIN deve ver link Alertas', () => {
      setup('ADMIN');
      fixture.detectChanges();
      expect(getLinkTexts(fixture)).toContain('Alertas');
    });

    it('OPERATOR não deve ver link Alertas', () => {
      setup('OPERATOR');
      expect(getLinkTexts(fixture)).not.toContain('Alertas');
    });
  });
});
