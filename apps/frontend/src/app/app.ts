import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith, catchError, EMPTY } from 'rxjs';
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
export class App implements OnInit {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  readonly shellState = inject(ShellStateService);

  readonly showNav = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects !== '/login'),
      startWith(this.router.url !== '/login'),
    ),
    { initialValue: true },
  );

  ngOnInit(): void {
    this.http.get('/api/actuator/health').pipe(catchError(() => EMPTY)).subscribe();
  }
}
