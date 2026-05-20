import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { AdminService, Shift, CreateShiftPayload } from '../admin.service';

function endAfterStartValidator(group: AbstractControl): ValidationErrors | null {
  const overnight = group.get('overnight')?.value as boolean;
  if (overnight) return null;
  const start = group.get('startTime')?.value as string;
  const end = group.get('endTime')?.value as string;
  if (!start || !end) return null;
  return end > start ? null : { endBeforeStart: true };
}

type DialogMode = 'create' | 'deactivate' | null;

@Component({
  selector: 'app-shift-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './shift-list.component.html',
  styleUrl: './shift-list.component.scss',
})
export class ShiftListComponent implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly shifts = signal<Shift[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly snackbar = signal<{ message: string; type: 'success' | 'error' } | null>(null);

  readonly dialogMode = signal<DialogMode>(null);
  readonly selectedShift = signal<Shift | null>(null);

  readonly form: FormGroup = this.fb.group(
    {
      name: ['', [Validators.required, Validators.maxLength(50)]],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      overnight: [false],
    },
    { validators: endAfterStartValidator },
  );

  ngOnInit(): void {
    this.loadShifts();
  }

  loadShifts(): void {
    this.loading.set(true);
    this.adminService.getShifts().subscribe({
      next: (list) => {
        this.shifts.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.showSnackbar('Erro ao carregar turnos.', 'error');
      },
    });
  }

  openCreateForm(): void {
    this.form.reset({ name: '', startTime: '', endTime: '', overnight: false });
    this.dialogMode.set('create');
  }

  closeForm(): void {
    this.dialogMode.set(null);
  }

  submitCreate(): void {
    if (this.form.invalid || this.submitting()) return;
    const v = this.form.value as CreateShiftPayload;
    this.submitting.set(true);
    this.adminService.createShift(v).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeForm();
        this.showSnackbar('Turno criado', 'success');
        this.loadShifts();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg: string = err?.error?.message ?? 'Erro ao criar turno.';
        this.showSnackbar(msg, 'error');
      },
    });
  }

  openDeactivateDialog(shift: Shift): void {
    this.selectedShift.set(shift);
    this.dialogMode.set('deactivate');
  }

  confirmDeactivate(): void {
    const shift = this.selectedShift();
    if (!shift) return;
    this.submitting.set(true);
    this.adminService.deactivateShift(shift.id).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogMode.set(null);
        this.selectedShift.set(null);
        this.showSnackbar('Turno desativado', 'success');
        this.loadShifts();
      },
      error: () => {
        this.submitting.set(false);
        this.dialogMode.set(null);
        this.selectedShift.set(null);
        this.showSnackbar('Erro ao desativar turno.', 'error');
      },
    });
  }

  cancelDeactivate(): void {
    this.dialogMode.set(null);
    this.selectedShift.set(null);
  }

  formatTimeRange(shift: Shift): string {
    const suffix = shift.overnight ? ' ✦' : '';
    return `${shift.startTime} – ${shift.endTime}${suffix}`;
  }

  get overnightCtrl() {
    return this.form.get('overnight');
  }

  private showSnackbar(message: string, type: 'success' | 'error'): void {
    this.snackbar.set({ message, type });
    timer(4000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.snackbar.set(null));
  }
}
