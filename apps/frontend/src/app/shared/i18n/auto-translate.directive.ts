import {
  AfterViewInit,
  Directive,
  ElementRef,
  OnDestroy,
  effect,
  inject,
} from '@angular/core';
import { I18nService } from './i18n.service';

const ATTRS = ['aria-label', 'title', 'placeholder'];
const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'SVG', 'PATH']);

@Directive({
  selector: '[appAutoTranslate]',
  standalone: true,
})
export class AutoTranslateDirective implements AfterViewInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly i18n = inject(I18nService);
  private readonly originals = new WeakMap<Node, string>();
  private readonly attrOriginals = new WeakMap<Element, Map<string, string>>();
  private observer?: MutationObserver;
  private queued = false;

  constructor() {
    effect(() => {
      this.i18n.currentLang();
      this.queueTranslate();
    });
  }

  ngAfterViewInit(): void {
    this.observer = new MutationObserver(() => this.queueTranslate());
    this.observer.observe(this.host.nativeElement, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ATTRS,
    });
    this.queueTranslate();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private queueTranslate(): void {
    if (this.queued) return;
    this.queued = true;
    queueMicrotask(() => {
      this.queued = false;
      this.translateTree(this.host.nativeElement);
    });
  }

  private translateTree(root: Node): void {
    if (this.shouldSkip(root)) return;

    if (root.nodeType === Node.TEXT_NODE) {
      this.translateText(root as Text);
      return;
    }

    if (root.nodeType !== Node.ELEMENT_NODE) return;

    const el = root as Element;
    this.translateAttributes(el);
    for (const child of Array.from(el.childNodes)) {
      this.translateTree(child);
    }
  }

  private translateText(node: Text): void {
    const raw = node.textContent ?? '';
    const trimmed = raw.trim();
    if (!this.isTranslatable(trimmed)) return;

    if (!this.originals.has(node)) {
      this.originals.set(node, trimmed);
    }

    const original = this.originals.get(node) ?? trimmed;
    const translated = this.i18n.translateLiteral(original);
    const next = raw.replace(trimmed, translated);
    if (node.textContent !== next) node.textContent = next;
  }

  private translateAttributes(el: Element): void {
    let map = this.attrOriginals.get(el);
    if (!map) {
      map = new Map<string, string>();
      this.attrOriginals.set(el, map);
    }

    for (const attr of ATTRS) {
      const value = el.getAttribute(attr);
      if (!value || !this.isTranslatable(value)) continue;
      if (!map.has(attr)) map.set(attr, value);
      const translated = this.i18n.translateLiteral(map.get(attr) ?? value);
      if (el.getAttribute(attr) !== translated) el.setAttribute(attr, translated);
    }
  }

  private shouldSkip(node: Node): boolean {
    if (node.nodeType !== Node.ELEMENT_NODE) return false;
    const el = node as Element;
    return SKIP_TAGS.has(el.tagName) || el.hasAttribute('data-no-auto-translate');
  }

  private isTranslatable(value: string): boolean {
    if (!value) return false;
    if (/^[\d\s.,:%/\\\-+()]+$/.test(value)) return false;
    if (/^[A-Z0-9_-]{2,12}$/.test(value)) return false;
    return true;
  }
}
