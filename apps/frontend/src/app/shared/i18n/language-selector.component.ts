import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { I18nService, SupportedLanguage } from './i18n.service';

@Component({
  selector: 'app-language-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="lang-selector">
      <select
        [value]="i18n.currentLang()"
        (change)="onSelect($event)"
        class="lang-select"
        [attr.aria-label]="'Idioma / Language'"
      >
        <option value="pt-BR">🇧🇷 PT</option>
        <option value="en-US">🇺🇸 EN</option>
        <option value="es-ES">🇪🇸 ES</option>
        <option value="fr-FR">🇫🇷 FR</option>
      </select>
    </div>
  `,
  styles: [`
    .lang-selector {
      display: flex;
      align-items: center;
    }
    .lang-select {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.3);
      border-radius: 4px;
      color: #fff;
      padding: 2px 6px;
      font-size: 13px;
      cursor: pointer;
      outline: none;
    }
    .lang-select:hover {
      border-color: rgba(255,255,255,0.6);
    }
    .lang-select option {
      background: #1F3A4A;
      color: #fff;
    }
  `]
})
export class LanguageSelectorComponent {
  readonly i18n = inject(I18nService);

  onSelect(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.i18n.setLanguage(select.value as SupportedLanguage);
  }
}
