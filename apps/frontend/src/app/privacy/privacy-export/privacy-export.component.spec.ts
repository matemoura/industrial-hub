import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PrivacyExportComponent } from './privacy-export.component';
import { UserService } from '../../admin/user.service';
import { AuthService } from '../../auth/auth.service';

function makeUserService(fail = false) {
  return {
    exportMyData: vi.fn().mockReturnValue(
      fail
        ? throwError(() => new Error('network error'))
        : of(new Blob(['{"profile":{}}'], { type: 'application/json' })),
    ),
  };
}

function makeAuthService(username = 'joao.silva') {
  return {
    username: vi.fn().mockReturnValue(username),
    role: vi.fn().mockReturnValue('OPERATOR'),
    isAuthenticated: vi.fn().mockReturnValue(true),
  };
}

async function createFixture(
  userSvc = makeUserService(),
  authSvc = makeAuthService(),
): Promise<ComponentFixture<PrivacyExportComponent>> {
  await TestBed.configureTestingModule({
    imports: [PrivacyExportComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: UserService, useValue: userSvc },
      { provide: AuthService, useValue: authSvc },
    ],
  }).compileComponents();

  const f = TestBed.createComponent(PrivacyExportComponent);
  f.detectChanges();
  return f;
}

describe('PrivacyExportComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('should create', async () => {
    const f = await createFixture();
    expect(f.componentInstance).toBeTruthy();
  });

  it('deve renderizar botão "Exportar meus dados"', async () => {
    const f = await createFixture();
    const btn = f.nativeElement.querySelector('[data-testid="btn-export"]');
    expect(btn).toBeTruthy();
    expect(btn.textContent).toContain('Exportar meus dados');
  });

  it('botão desabilitado enquanto loading é true', async () => {
    const f = await createFixture();
    f.componentInstance.loading.set(true);
    f.detectChanges();
    const btn = f.nativeElement.querySelector('[data-testid="btn-export"]');
    expect(btn.disabled).toBe(true);
  });

  it('chama exportMyData ao clicar no botão', async () => {
    const userSvc = makeUserService();
    const f = await createFixture(userSvc);

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    // Spy apenas na propriedade click do anchor criado pelo componente
    const origCreateElement = document.createElement.bind(document);
    let createdAnchor: HTMLAnchorElement | null = null;
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = origCreateElement(tag);
      if (tag === 'a') {
        createdAnchor = el as HTMLAnchorElement;
        vi.spyOn(createdAnchor, 'click').mockImplementation(() => undefined);
      }
      return el;
    });

    f.nativeElement.querySelector('[data-testid="btn-export"]').click();
    f.detectChanges();

    expect(userSvc.exportMyData).toHaveBeenCalledOnce();

    vi.restoreAllMocks();
  });

  it('exibe mensagem de sucesso após download', async () => {
    const userSvc = makeUserService();
    const authSvc = makeAuthService('joao.silva');
    const f = await createFixture(userSvc, authSvc);

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const origCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = origCreateElement(tag);
      if (tag === 'a') vi.spyOn(el as HTMLAnchorElement, 'click').mockImplementation(() => undefined);
      return el;
    });

    f.componentInstance.exportData();
    f.detectChanges();

    const msg = f.nativeElement.querySelector('[data-testid="success-msg"]');
    expect(msg).toBeTruthy();
    expect(msg.textContent).toContain('meus-dados-joao_silva.json');

    vi.restoreAllMocks();
  });

  it('exibe mensagem de erro quando exportMyData falha', async () => {
    const f = await createFixture(makeUserService(true));
    f.componentInstance.exportData();
    f.detectChanges();
    const errEl = f.nativeElement.querySelector('[data-testid="error-msg"]');
    expect(errEl).toBeTruthy();
    expect(errEl.textContent).toContain('Erro ao exportar dados');
  });

  it('filename usa username do AuthService', async () => {
    const userSvc = makeUserService();
    const authSvc = makeAuthService('maria.santos');
    const f = await createFixture(userSvc, authSvc);

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let capturedAnchor: HTMLAnchorElement | null = null;
    const origCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = origCreateElement(tag);
      if (tag === 'a') {
        capturedAnchor = el as HTMLAnchorElement;
        vi.spyOn(capturedAnchor, 'click').mockImplementation(() => undefined);
      }
      return el;
    });

    f.componentInstance.exportData();
    f.detectChanges();

    expect(capturedAnchor).not.toBeNull();
    expect((capturedAnchor as unknown as HTMLAnchorElement).download).toBe('meus-dados-maria_santos.json');

    vi.restoreAllMocks();
  });

  it('(SEC-087) filename sanitiza caracteres especiais no username', async () => {
    const userSvc = makeUserService();
    const authSvc = makeAuthService('user with spaces');
    const f = await createFixture(userSvc, authSvc);

    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let capturedAnchor: HTMLAnchorElement | null = null;
    const origCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = origCreateElement(tag);
      if (tag === 'a') {
        capturedAnchor = el as HTMLAnchorElement;
        vi.spyOn(capturedAnchor, 'click').mockImplementation(() => undefined);
      }
      return el;
    });

    f.componentInstance.exportData();
    f.detectChanges();

    expect(capturedAnchor).not.toBeNull();
    // Espaços devem ser substituídos por underscore
    const download = (capturedAnchor as unknown as HTMLAnchorElement).download;
    expect(download).toMatch(/^meus-dados-[a-zA-Z0-9_-]+\.json$/);
    expect(download).not.toContain(' ');

    vi.restoreAllMocks();
  });
});
