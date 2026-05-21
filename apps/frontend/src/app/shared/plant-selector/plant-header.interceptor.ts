import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PlantContextService } from './plant-context.service';

export const plantHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/') || req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  const plantContext = inject(PlantContextService);
  const headers = plantContext.getHeader();

  if (Object.keys(headers).length === 0) {
    return next(req);
  }

  return next(req.clone({ setHeaders: headers }));
};
