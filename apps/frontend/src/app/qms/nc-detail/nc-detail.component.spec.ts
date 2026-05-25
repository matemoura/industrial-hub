import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { NcDetailComponent } from './nc-detail.component';
import { QmsService } from '../qms.service';
import { AuthService } from '../../auth/auth.service';
import { AttachmentService } from '../../shared/attachment/attachment.service';
import { NetworkStatusService } from '../../shared/offline/network-status.service';

function makeRoute(id = 'nc-1') {
  return { snapshot: { paramMap: { get: () => id } } };
}

function makeAuthService(role: string) {
  return { role: signal(role) };
}

describe('NcDetailComponent', () => {
  let fixture: ComponentFixture<NcDetailComponent>;
  let component: NcDetailComponent;

  function setup(online = true) {
    const qmsService = {
      getNc: vi.fn().mockReturnValue(of(null)),
      listActions: vi.fn().mockReturnValue(of([])),
      listSuppliers: vi.fn().mockReturnValue(of([])),
      transitionStatus: vi.fn(),
      createAction: vi.fn(),
      completeAction: vi.fn(),
      deleteAction: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [NcDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QmsService, useValue: qmsService },
        { provide: AuthService, useValue: makeAuthService('OPERATOR') },
        { provide: ActivatedRoute, useValue: makeRoute('nc-1') },
        { provide: NetworkStatusService, useValue: { isOnline: signal(online) } },
        {
          provide: AttachmentService,
          useValue: {
            list: vi.fn().mockReturnValue(of([])),
            upload: vi.fn(),
            getDownloadUrl: vi.fn().mockReturnValue(of({ url: '' })),
            delete: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NcDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    return qmsService;
  }

  // MF-S26-01 — offline guard: no API calls, error message shown
  it('should show offline error and skip API calls when offline', () => {
    const qmsService = setup(false);
    expect((qmsService.getNc as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
    expect((qmsService.listActions as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
    expect(component.errorMsg()).toContain('Sem conexão');
    expect(component.loading()).toBe(false);
  });

  // Online — API calls are made normally
  it('should call API when online', () => {
    const qmsService = setup(true);
    expect((qmsService.getNc as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
    expect((qmsService.listActions as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
  });
});
