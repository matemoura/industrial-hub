import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { NavComponent } from './shared/nav/nav.component';
import { SidebarComponent } from './shared/shell/sidebar.component';
import { ShellStateService } from './shared/shell/shell-state.service';
import { AutoTranslateDirective } from './shared/i18n/auto-translate.directive';

@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, NavComponent, SidebarComponent, AutoTranslateDirective],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  readonly shellState = inject(ShellStateService);

  readonly showNav = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects !== '/login'),
      startWith(this.router.url !== '/login'),
    ),
    { initialValue: true },
  );
}
