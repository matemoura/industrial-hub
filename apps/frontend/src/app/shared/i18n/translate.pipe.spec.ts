import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslatePipe } from './translate.pipe';
import { I18nService } from './i18n.service';

describe('TranslatePipe', () => {
  let pipe: TranslatePipe;
  let translateFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    translateFn = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: I18nService, useValue: { translate: translateFn, currentLang: () => 'pt-BR' } }
      ]
    });
    pipe = TestBed.runInInjectionContext(() => new TranslatePipe());
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('delegates to I18nService.translate', () => {
    translateFn.mockReturnValue('Salvar');
    expect(pipe.transform('common.save')).toBe('Salvar');
    expect(translateFn).toHaveBeenCalledWith('common.save', undefined);
  });

  it('returns key when service returns key', () => {
    translateFn.mockReturnValue('common.missing');
    expect(pipe.transform('common.missing')).toBe('common.missing');
  });
});
