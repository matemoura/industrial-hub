import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserPermissionsComponent } from './user-permissions.component';
import { UserModulePermissionResponse } from '../user.service';
import { ComponentRef } from '@angular/core';

const MOCK_PERMISSIONS: UserModulePermissionResponse[] = [
  { module: 'OEE',               canView: true,  canCreate: true,  canEdit: true,  canDelete: false },
  { module: 'QMS',               canView: true,  canCreate: false, canEdit: false, canDelete: false },
  { module: 'MAINTENANCE',       canView: true,  canCreate: true,  canEdit: true,  canDelete: false },
  { module: 'PRODUCTION',        canView: false, canCreate: false, canEdit: false, canDelete: false },
  { module: 'TRAINING',          canView: true,  canCreate: false, canEdit: false, canDelete: false },
  { module: 'CHANGES',           canView: false, canCreate: false, canEdit: false, canDelete: false },
  { module: 'MANAGEMENT_REVIEW', canView: true,  canCreate: false, canEdit: false, canDelete: false },
];

describe('UserPermissionsComponent', () => {
  let httpTesting: HttpTestingController;

  function createComponent(): { fixture: ReturnType<typeof TestBed.createComponent<UserPermissionsComponent>>; comp: UserPermissionsComponent; ref: ComponentRef<UserPermissionsComponent> } {
    const fixture = TestBed.createComponent(UserPermissionsComponent);
    const ref = fixture.componentRef;
    ref.setInput('userId', 'user-123');
    fixture.detectChanges();
    // flush the GET permissions request
    httpTesting.expectOne('/api/v1/admin/users/user-123/permissions').flush(MOCK_PERMISSIONS);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, ref };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserPermissionsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  // US-140 (a) — renderiza 7 linhas (uma por módulo)
  it('US-140 (a): renders 7 rows — one per module', () => {
    const { fixture } = createComponent();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid^="row-"]');
    expect(rows.length).toBe(7);
  });

  // US-140 (b) — checkbox canView marcado quando permission.canView=true
  it('US-140 (b): canView checkbox is checked when permission.canView is true', () => {
    const { fixture } = createComponent();
    const oeeViewCb = fixture.nativeElement.querySelector(
      '[data-testid="perm-OEE-canView"]',
    ) as HTMLInputElement;
    expect(oeeViewCb.checked).toBe(true);

    const productionViewCb = fixture.nativeElement.querySelector(
      '[data-testid="perm-PRODUCTION-canView"]',
    ) as HTMLInputElement;
    expect(productionViewCb.checked).toBe(false);
  });

  // US-140 (c) — click em salvar chama updateUserPermissions()
  it('US-140 (c): clicking save calls updateUserPermissions()', () => {
    const { fixture } = createComponent();
    const saveBtn = fixture.nativeElement.querySelector(
      '[data-testid="btn-save-perms"]',
    ) as HTMLButtonElement;
    saveBtn.click();
    fixture.detectChanges();

    const req = httpTesting.expectOne('/api/v1/admin/users/user-123/permissions');
    expect(req.request.method).toBe('PUT');
    req.flush(MOCK_PERMISSIONS);
  });

  // US-140 (d) — botão salvar disabled durante isSaving=true
  it('US-140 (d): save button is disabled while isSaving is true', () => {
    const { fixture, comp } = createComponent();
    comp.isSaving.set(true);
    fixture.detectChanges();

    const saveBtn = fixture.nativeElement.querySelector(
      '[data-testid="btn-save-perms"]',
    ) as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);
  });
});
