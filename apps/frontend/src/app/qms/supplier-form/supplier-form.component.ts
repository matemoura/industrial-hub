import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { QmsService, SupplierResponse } from '../qms.service';

@Component({
  selector: 'app-supplier-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './supplier-form.component.html',
  styleUrl: './supplier-form.component.scss',
})
export class SupplierFormComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  editId = signal<string | null>(null);
  existing = signal<SupplierResponse | null>(null);

  code = signal('');
  name = signal('');
  contactEmail = signal('');
  contactPhone = signal('');
  address = signal('');
  onboardedAt = signal('');

  loading = signal(false);
  fetchLoading = signal(false);
  errorMsg = signal<string | null>(null);

  get isEditMode(): boolean {
    return this.editId() !== null;
  }

  get isValid(): boolean {
    return this.code().trim().length > 0 && this.name().trim().length > 0;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId.set(id);
      this.fetchLoading.set(true);
      this.qmsService.getSupplier(id).subscribe({
        next: (s) => {
          this.existing.set(s);
          this.code.set(s.code);
          this.name.set(s.name);
          this.contactEmail.set(s.contactEmail ?? '');
          this.contactPhone.set(s.contactPhone ?? '');
          this.address.set(s.address ?? '');
          this.onboardedAt.set(s.onboardedAt ?? '');
          this.fetchLoading.set(false);
        },
        error: () => {
          this.errorMsg.set('Fornecedor não encontrado.');
          this.fetchLoading.set(false);
        },
      });
    }
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;

    this.loading.set(true);
    this.errorMsg.set(null);

    const payload = {
      code: this.code().trim(),
      name: this.name().trim(),
      contactEmail: this.contactEmail().trim() || undefined,
      contactPhone: this.contactPhone().trim() || undefined,
      address: this.address().trim() || undefined,
      onboardedAt: this.onboardedAt() || undefined,
    };

    const request$ = this.isEditMode
      ? this.qmsService.updateSupplier(this.editId()!, payload)
      : this.qmsService.createSupplier(payload);

    request$.subscribe({
      next: (saved) => {
        this.loading.set(false);
        this.router.navigate(['/qms/suppliers', saved.id], {
          state: { toast: this.isEditMode ? 'Fornecedor atualizado com sucesso' : 'Fornecedor cadastrado com sucesso' },
        });
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao salvar fornecedor. Tente novamente.');
        this.loading.set(false);
      },
    });
  }
}
