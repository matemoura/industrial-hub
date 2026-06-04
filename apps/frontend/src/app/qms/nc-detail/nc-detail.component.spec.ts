import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { NcDetailComponent } from './nc-detail.component';
import { QmsService, NcDocumentLinkResponse } from '../qms.service';
import { GedService } from '../ged.service';
import { AuthService } from '../../auth/auth.service';
import { AttachmentService } from '../../shared/attachment/attachment.service';
import { NetworkStatusService } from '../../shared/offline/network-status.service';

function makeRoute(id = 'nc-1') {
  return { snapshot: { paramMap: { get: () => id } } };
}

function makeAuthService(role: string) {
  return { role: signal(role) };
}

const mockNcDocLinks: NcDocumentLinkResponse[] = [
  {
    linkId: 'link-1',
    documentId: 'doc-1',
    documentCode: 'SOP-001',
    documentTitle: 'Procedimento A',
    documentCategory: 'SOP',
    documentStatus: 'PUBLISHED',
    linkType: 'PROCEDURE_AT_OCCURRENCE',
    linkedAt: '2026-06-01T10:00:00',
  },
];

describe('NcDetailComponent', () => {
  let fixture: ComponentFixture<NcDetailComponent>;
  let component: NcDetailComponent;

  function setup(role = 'OPERATOR', online = true) {
    const qmsService = {
      getNc: vi.fn().mockReturnValue(of(null)),
      listActions: vi.fn().mockReturnValue(of([])),
      listNcDocuments: vi.fn().mockReturnValue(of([])),
      listSuppliers: vi.fn().mockReturnValue(of([])),
      transitionStatus: vi.fn(),
      createAction: vi.fn(),
      completeAction: vi.fn(),
      deleteAction: vi.fn(),
      linkNcToDocument: vi.fn(),
      unlinkNcDocument: vi.fn(),
    };

    const gedService = {
      listDocuments: vi.fn().mockReturnValue(of({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 })),
    };

    TestBed.configureTestingModule({
      imports: [NcDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QmsService, useValue: qmsService },
        { provide: GedService, useValue: gedService },
        { provide: AuthService, useValue: makeAuthService(role) },
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
    return { qmsService, gedService };
  }

  // MF-S26-01 — offline guard: no API calls, error message shown
  it('should show offline error and skip API calls when offline', () => {
    const { qmsService } = setup('OPERATOR', false);
    expect((qmsService.getNc as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
    expect((qmsService.listActions as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
    expect(component.errorMsg()).toContain('Sem conexão');
    expect(component.loading()).toBe(false);
  });

  // Online — API calls are made normally
  it('should call API when online', () => {
    const { qmsService } = setup('OPERATOR');
    expect((qmsService.getNc as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
    expect((qmsService.listActions as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
  });

  // US-115-AC1: loadNcDocLinks called on init
  it('should call listNcDocuments on init', () => {
    const { qmsService } = setup('OPERATOR');
    expect((qmsService.listNcDocuments as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
    expect((qmsService.listNcDocuments as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBe('nc-1');
  });

  // US-115-AC2: initial state is empty
  it('should initialise ncDocLinks as empty array', () => {
    setup('OPERATOR');
    expect(component.ncDocLinks()).toEqual([]);
    expect(component.ncDocLinksLoading()).toBe(false);
  });

  // US-115-AC3: supervisor sees isSupervisor=true
  it('should expose isSupervisor true for SUPERVISOR role', () => {
    setup('SUPERVISOR');
    expect(component.isSupervisor).toBe(true);
  });

  // US-115-AC4: operator cannot see link button
  it('should expose isSupervisor false for OPERATOR role', () => {
    setup('OPERATOR');
    expect(component.isSupervisor).toBe(false);
  });

  // US-115-AC5: openLinkDocModal initialises modal state
  it('should initialise link doc modal state on open', () => {
    setup('SUPERVISOR');
    component.openLinkDocModal();
    expect(component.showLinkDocModal()).toBe(true);
    expect(component.linkDocSearch()).toBe('');
    expect(component.linkDocSelectedId()).toBeNull();
    expect(component.linkDocLinkType()).toBe('PROCEDURE_AT_OCCURRENCE');
  });

  // US-115-AC6: close modal
  it('should close link doc modal on closeLinkDocModal()', () => {
    setup('SUPERVISOR');
    component.openLinkDocModal();
    component.closeLinkDocModal();
    expect(component.showLinkDocModal()).toBe(false);
  });

  // US-115-AC7: unlinkDocument removes entry from list
  it('should remove link from ncDocLinks on unlinkDocument', () => {
    const { qmsService } = setup('SUPERVISOR');
    (qmsService.unlinkNcDocument as ReturnType<typeof vi.fn>).mockReturnValue(of(undefined));

    component.nc.set({
      id: 'nc-1',
      title: 'Test NC',
      type: 'PROCESS',
      severity: 'HIGH',
      status: 'OPEN',
      reportedBy: 'user1',
      reportedAt: '2026-01-01T00:00:00',
      slaBreached: false,
      description: null,
      closedAt: null,
      closedBy: null,
      supplierId: null,
      supplierName: null,
      actions: [],
      rca: null,
    });

    component.ncDocLinks.set([...mockNcDocLinks]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.unlinkDocument('doc-1');

    expect((qmsService.unlinkNcDocument as ReturnType<typeof vi.fn>).mock.calls.length).toBe(1);
    expect(component.ncDocLinks()).toHaveLength(0);
  });

  // US-115-AC8: selectLinkDoc sets selected id
  it('should set linkDocSelectedId on selectLinkDoc()', () => {
    setup('SUPERVISOR');
    component.selectLinkDoc('doc-abc');
    expect(component.linkDocSelectedId()).toBe('doc-abc');
  });
});
