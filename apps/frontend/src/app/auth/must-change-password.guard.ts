import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const mustChangePasswordGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated() && auth.mustChangePassword()) {
    return inject(Router).createUrlTree(['/change-password']);
  }
  return true;
};
