import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { QmsService, SupplierResponse } from '../qms.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-supplier-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './supplier-list.component.html',
  styleUrl: './supplier-list.component.scss',
})
export class SupplierListComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.authService.role;

  suppliers = signal<SupplierResponse[]>([]);
  loading = signal(true);
  toast = signal<string | null>(null);

  get isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    const toastMsg = (history.state as { toast?: string })?.toast;
    if (toastMsg) {
      this.toast.set(toastMsg);
      setTimeout(() => this.toast.set(null), 4000);
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.qmsService.listSuppliers().subscribe({
      next: (list) => {
        this.suppliers.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openDetail(id: string): void {
    this.router.navigate(['/qms/suppliers', id]);
  }
}
