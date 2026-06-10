import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { I18nService } from './i18n.service';

export const acceptLanguageInterceptor: HttpInterceptorFn = (req, next) => {
  const i18n = inject(I18nService);
  const lang = i18n.currentLang();
  const cloned = req.clone({
    setHeaders: { 'Accept-Language': lang }
  });
  return next(cloned);
};
