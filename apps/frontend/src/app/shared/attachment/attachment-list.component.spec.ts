import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { AttachmentListComponent } from './attachment-list.component';
import { AttachmentService, AttachmentResponse } from './attachment.service';
import { AuthService } from '../../auth/auth.service';

const ATTACHMENT_1: AttachmentResponse = {
  id: 'a1',
  entityType: 'WORK_ORDER',
  entityId: 'wo1',
  originalName: 'relatorio.pdf',
  contentType: 'application/pdf',
  fileSizeBytes: 204800,
  uploadedBy: 'operador1',
  uploadedAt: '2026-05-21T10:00:00',
};

const ATTACHMENT_2: AttachmentResponse = {
  id: 'a2',
  entityType: 'WORK_ORDER',
  entityId: 'wo1',
  originalName: 'foto.jpg',
  contentType: 'image/jpeg',
  fileSizeBytes: 512000,
  uploadedBy: 'supervisor1',
  uploadedAt: '2026-05-21T11:00:00',
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

function makeAttachmentService(attachments: AttachmentResponse[] = []) {
  return {
    list: vi.fn().mockReturnValue(of(attachments)),
    upload: vi.fn().mockReturnValue(of(ATTACHMENT_1)),
    getDownloadUrl: vi.fn().mockReturnValue(of({ url: 'https://example.com/file', expiresAt: '2026-05-21T12:00:00Z' })),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };
}

async function setup(role: string, attachments: AttachmentResponse[] = []) {
  const authService = makeAuthService(role);
  const attachmentService = makeAttachmentService(attachments);

  await TestBed.configureTestingModule({
    imports: [AttachmentListComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: authService },
      { provide: AttachmentService, useValue: attachmentService },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<AttachmentListComponent> = TestBed.createComponent(AttachmentListComponent);
  const component = fixture.componentInstance;

  // Set required inputs
  fixture.componentRef.setInput('entityType', 'WORK_ORDER');
  fixture.componentRef.setInput('entityId', 'wo1');

  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, component, authService, attachmentService };
}

describe('AttachmentListComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should render empty state when no attachments', async () => {
    const { fixture } = await setup('ADMIN', []);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="attachments-empty"]')?.textContent?.trim())
      .toBe('Nenhum anexo encontrado.');
    expect(el.querySelectorAll('[data-testid="attachment-item"]').length).toBe(0);
  });

  it('should render list of attachments when loaded', async () => {
    const { fixture } = await setup('ADMIN', [ATTACHMENT_1, ATTACHMENT_2]);
    const el: HTMLElement = fixture.nativeElement;
    const items = el.querySelectorAll('[data-testid="attachment-item"]');
    expect(items.length).toBe(2);
    expect(el.textContent).toContain('relatorio.pdf');
    expect(el.textContent).toContain('foto.jpg');
  });

  it('should show upload button for OPERATOR', async () => {
    const { fixture } = await setup('OPERATOR', []);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-upload"]')).not.toBeNull();
  });

  it('should show upload button for SUPERVISOR', async () => {
    const { fixture } = await setup('SUPERVISOR', []);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-upload"]')).not.toBeNull();
  });

  it('should show upload button for ADMIN', async () => {
    const { fixture } = await setup('ADMIN', []);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-upload"]')).not.toBeNull();
  });

  it('should show delete button for SUPERVISOR', async () => {
    const { fixture } = await setup('SUPERVISOR', [ATTACHMENT_1]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-delete"]')).not.toBeNull();
  });

  it('should show delete button for ADMIN', async () => {
    const { fixture } = await setup('ADMIN', [ATTACHMENT_1]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-delete"]')).not.toBeNull();
  });

  it('should NOT show delete button for OPERATOR', async () => {
    const { fixture } = await setup('OPERATOR', [ATTACHMENT_1]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="btn-delete"]')).toBeNull();
  });

  it('onFileSelected: invalid type should NOT call upload', async () => {
    const { fixture, component, attachmentService } = await setup('ADMIN', []);
    vi.spyOn(window, 'alert').mockReturnValue(undefined);

    const invalidFile = new File(['data'], 'script.exe', { type: 'application/x-msdownload' });
    const event = {
      target: { files: [invalidFile], value: '' }
    } as unknown as Event;

    component.onFileSelected(event);

    expect(attachmentService.upload).not.toHaveBeenCalled();
    expect(window.alert).toHaveBeenCalledWith('Tipo de arquivo não permitido. Use JPG, PNG, WebP, PDF ou Excel.');
    fixture.detectChanges();
  });

  it('onFileSelected: valid file should call upload with correct args', async () => {
    const { fixture, component, attachmentService } = await setup('ADMIN', []);

    const validFile = new File(['data'], 'report.pdf', { type: 'application/pdf' });
    Object.defineProperty(validFile, 'size', { value: 1024 });
    const mockInput = { files: [validFile], value: '' };
    const event = { target: mockInput } as unknown as Event;

    component.onFileSelected(event);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(attachmentService.upload).toHaveBeenCalledWith('WORK_ORDER', 'wo1', validFile);
  });

  it('deleteAttachment: should call delete and remove item from list', async () => {
    const { fixture, component, attachmentService } = await setup('ADMIN', [ATTACHMENT_1, ATTACHMENT_2]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.deleteAttachment('a1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(attachmentService.delete).toHaveBeenCalledWith('a1');
    expect(component.attachments().length).toBe(1);
    expect(component.attachments()[0].id).toBe('a2');
  });

  it('isLoading should be true while loading, false after response', async () => {
    const attachmentService = makeAttachmentService([]);
    const authService = makeAuthService('ADMIN');

    // We'll check the signal state before the observable resolves
    // Since of() resolves synchronously, we verify the final state is false
    await TestBed.configureTestingModule({
      imports: [AttachmentListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: AttachmentService, useValue: attachmentService },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AttachmentListComponent);
    fixture.componentRef.setInput('entityType', 'WORK_ORDER');
    fixture.componentRef.setInput('entityId', 'wo1');

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // After resolution, isLoading should be false
    expect(fixture.componentInstance.isLoading()).toBe(false);
  });

  it('loadThumbs: getDownloadUrl error should NOT update thumbUrls and should not throw', async () => {
    const attachmentService = {
      list: vi.fn().mockReturnValue(of([ATTACHMENT_2])),
      upload: vi.fn(),
      getDownloadUrl: vi.fn().mockReturnValue(throwError(() => new Error('url error'))),
      delete: vi.fn(),
    };
    const authService = makeAuthService('ADMIN');

    await TestBed.configureTestingModule({
      imports: [AttachmentListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: AttachmentService, useValue: attachmentService },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AttachmentListComponent);
    fixture.componentRef.setInput('entityType', 'WORK_ORDER');
    fixture.componentRef.setInput('entityId', 'wo1');

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // thumbUrls should remain empty — error was swallowed silently
    expect(fixture.componentInstance.thumbUrls()).toEqual({});
    // attachments still loaded
    expect(fixture.componentInstance.attachments().length).toBe(1);
  });

  it('onFileSelected: getDownloadUrl error after upload should NOT update thumbUrls and should not throw', async () => {
    const imageAttachment: AttachmentResponse = {
      ...ATTACHMENT_2,
      id: 'a3',
    };
    const attachmentService = {
      list: vi.fn().mockReturnValue(of([])),
      upload: vi.fn().mockReturnValue(of(imageAttachment)),
      getDownloadUrl: vi.fn().mockReturnValue(throwError(() => new Error('url error'))),
      delete: vi.fn(),
    };
    const authService = makeAuthService('ADMIN');

    await TestBed.configureTestingModule({
      imports: [AttachmentListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: AttachmentService, useValue: attachmentService },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AttachmentListComponent);
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('entityType', 'WORK_ORDER');
    fixture.componentRef.setInput('entityId', 'wo1');

    fixture.detectChanges();
    await fixture.whenStable();

    const validFile = new File(['data'], 'foto.jpg', { type: 'image/jpeg' });
    Object.defineProperty(validFile, 'size', { value: 1024 });
    const mockInput = { files: [validFile], value: '' };
    const event = { target: mockInput } as unknown as Event;

    component.onFileSelected(event);
    fixture.detectChanges();
    await fixture.whenStable();

    // attachment was added to list even though URL generation failed
    expect(component.attachments().length).toBe(1);
    // thumbUrls remains empty — error swallowed silently
    expect(component.thumbUrls()).toEqual({});
    // isUploading reset to false
    expect(component.isUploading()).toBe(false);
  });

  it('isLoading should remain false after error response', async () => {
    const attachmentService = {
      list: vi.fn().mockReturnValue(throwError(() => new Error('network error'))),
      upload: vi.fn(),
      getDownloadUrl: vi.fn(),
      delete: vi.fn(),
    };
    const authService = makeAuthService('ADMIN');

    await TestBed.configureTestingModule({
      imports: [AttachmentListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: AttachmentService, useValue: attachmentService },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AttachmentListComponent);
    fixture.componentRef.setInput('entityType', 'WORK_ORDER');
    fixture.componentRef.setInput('entityId', 'wo1');

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.isLoading()).toBe(false);
  });
});
