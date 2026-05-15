import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { UserService } from '../../admin/user.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup(
    {
      currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] }),
      confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: this.passwordsMatchValidator },
  );

  private passwordsMatchValidator(group: FormGroup): { mismatch: true } | null {
    const np = (group.get('newPassword') as FormControl).value;
    const cp = (group.get('confirmPassword') as FormControl).value;
    return np && cp && np !== cp ? { mismatch: true } : null;
  }

  get passwordsMismatch(): boolean {
    return this.form.hasError('mismatch') && (this.form.get('confirmPassword')?.touched ?? false);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) return;

    this.loading.set(true);
    this.error.set(null);

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.userService.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res) => {
        this.authService.updateToken(res.token);
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Erro ao alterar senha. Tente novamente.');
      },
    });
  }
}
