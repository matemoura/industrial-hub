import { Injectable, signal, effect } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export type SupportedLanguage = 'pt-BR' | 'en-US' | 'es-ES' | 'fr-FR';

const SUPPORTED: SupportedLanguage[] = ['pt-BR', 'en-US', 'es-ES', 'fr-FR'];
const STORAGE_KEY = 'msb_lang';
const DEFAULT_LANG: SupportedLanguage = 'pt-BR';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private _translations = signal<Record<string, unknown>>({});
  readonly currentLang = signal<SupportedLanguage>(this.detectInitialLang());

  constructor(private http: HttpClient) {
    effect(() => {
      const lang = this.currentLang();
      this.loadTranslations(lang);
    });
  }

  private detectInitialLang(): SupportedLanguage {
    const stored = localStorage.getItem(STORAGE_KEY) as SupportedLanguage | null;
    if (stored && SUPPORTED.includes(stored)) return stored;

    const browser = navigator.language;
    const match = SUPPORTED.find(l => browser.startsWith(l.split('-')[0]));
    return match ?? DEFAULT_LANG;
  }

  private loadTranslations(lang: SupportedLanguage): void {
    this.http.get<Record<string, unknown>>(`/assets/i18n/${lang}.json`).subscribe({
      next: data => this._translations.set(data),
      error: () => {
        if (lang !== DEFAULT_LANG) {
          this.http.get<Record<string, unknown>>(`/assets/i18n/${DEFAULT_LANG}.json`).subscribe({
            next: data => this._translations.set(data)
          });
        }
      }
    });
  }

  setLanguage(lang: SupportedLanguage): void {
    localStorage.setItem(STORAGE_KEY, lang);
    this.currentLang.set(lang);
  }

  translate(key: string, params?: Record<string, string>): string {
    const keys = key.split('.');
    let value: unknown = this._translations();
    for (const k of keys) {
      if (value && typeof value === 'object') {
        value = (value as Record<string, unknown>)[k];
      } else {
        return key;
      }
    }
    if (typeof value !== 'string') return key;
    if (!params) return value;
    return Object.entries(params).reduce(
      (acc, [k, v]) => acc.replace(new RegExp(`\\{\\{${k}\\}\\}`, 'g'), v),
      value
    );
  }
}
