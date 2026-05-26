import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { OfflineQueueService } from '../shared/offline/offline-queue.service';

describe('AuthService — SEC-089 AC#6', () => {
  let authService: AuthService;
  let clearAllMock: ReturnType<typeof vi.fn>;
  let routerNavigateMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    clearAllMock = vi.fn().mockResolvedValue(undefined);
    routerNavigateMock = vi.fn().mockResolvedValue(true);

    // Ensure a clean localStorage state before each test
    localStorage.removeItem('msb_token');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        {
          provide: OfflineQueueService,
          useValue: { clearAll: clearAllMock },
        },
        {
          provide: Router,
          useValue: { navigate: routerNavigateMock },
        },
      ],
    });

    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem('msb_token');
    vi.clearAllMocks();
  });

  it('logout() chama clearAll() antes de remover o token do localStorage e navega para /login', async () => {
    // Arrange — seed a fake token so we can assert it is removed
    localStorage.setItem('msb_token', 'fake.jwt.token');

    // Track call order
    const callOrder: string[] = [];
    clearAllMock.mockImplementation(async () => {
      callOrder.push('clearAll');
    });
    routerNavigateMock.mockImplementation(async () => {
      callOrder.push('navigate');
      return true;
    });

    // Act
    await authService.logout();

    // Assert — clearAll was called
    expect(clearAllMock).toHaveBeenCalledTimes(1);

    // Assert — token removed from localStorage after logout
    expect(localStorage.getItem('msb_token')).toBeNull();

    // Assert — router navigated to /login
    expect(routerNavigateMock).toHaveBeenCalledWith(['/login']);

    // Assert — clearAll happened before navigate (clearAll clears queue first)
    expect(callOrder.indexOf('clearAll')).toBeLessThan(callOrder.indexOf('navigate'));
  });
});
