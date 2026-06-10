import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { I18nService } from './i18n.service';

/** Force pt-BR detection in jsdom (navigator.language defaults to 'en-US'). */
function setupService(storedLang?: string) {
  localStorage.clear();
  if (storedLang) {
    localStorage.setItem('msb_lang', storedLang);
  } else {
    // Ensure no stored lang — service will fall through to navigator.language detection.
    // Since jsdom defaults navigator.language to 'en-US', we pin pt-BR explicitly.
    localStorage.setItem('msb_lang', 'pt-BR');
  }
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      I18nService
    ]
  });
  const service = TestBed.inject(I18nService);
  const httpMock = TestBed.inject(HttpTestingController);
  TestBed.flushEffects();
  return { service, httpMock };
}

describe('I18nService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  it('loads pt-BR by default', () => {
    const { service, httpMock } = setupService();
    const req = httpMock.expectOne('/assets/i18n/pt-BR.json');
    req.flush({ common: { save: 'Salvar' } });
    expect(service.currentLang()).toBe('pt-BR');
    httpMock.verify();
  });

  it('translate returns key when not loaded', () => {
    const { service, httpMock } = setupService();
    httpMock.expectOne('/assets/i18n/pt-BR.json').flush({});
    expect(service.translate('common.save')).toBe('common.save');
    httpMock.verify();
  });

  it('translate returns value after load', () => {
    const { service, httpMock } = setupService();
    httpMock.expectOne('/assets/i18n/pt-BR.json').flush({ common: { save: 'Salvar' } });
    expect(service.translate('common.save')).toBe('Salvar');
    httpMock.verify();
  });

  it('setLanguage changes currentLang and persists to localStorage', () => {
    const { service, httpMock } = setupService();
    httpMock.expectOne('/assets/i18n/pt-BR.json').flush({});
    service.setLanguage('en-US');
    TestBed.flushEffects();
    httpMock.expectOne('/assets/i18n/en-US.json').flush({ common: { save: 'Save' } });
    expect(service.currentLang()).toBe('en-US');
    expect(localStorage.getItem('msb_lang')).toBe('en-US');
    httpMock.verify();
  });

  it('setLanguage to es-ES loads Spanish', () => {
    const { service, httpMock } = setupService();
    httpMock.expectOne('/assets/i18n/pt-BR.json').flush({});
    service.setLanguage('es-ES');
    TestBed.flushEffects();
    const req = httpMock.expectOne('/assets/i18n/es-ES.json');
    req.flush({ common: { save: 'Guardar' } });
    expect(service.translate('common.save')).toBe('Guardar');
    httpMock.verify();
  });
});
