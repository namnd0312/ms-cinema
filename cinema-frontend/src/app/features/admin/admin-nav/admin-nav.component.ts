import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-admin-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="panel">
      @for (link of links; track link.path) {
        <a mat-tab-link [routerLink]="link.path" routerLinkActive #rla="routerLinkActive"
           [active]="rla.isActive">{{ link.label }}</a>
      }
    </nav>
    <mat-tab-nav-panel #panel>
      <router-outlet></router-outlet>
    </mat-tab-nav-panel>
  `,
  styles: [`
    nav { margin-bottom: 16px; }
  `]
})
export class AdminNavComponent {
  links = [
    { path: 'movies', label: 'Movies' },
    { path: 'theaters', label: 'Theaters' },
    { path: 'showtimes', label: 'Showtimes' },
    { path: 'payments', label: 'Payments' },
    { path: 'reconciliation', label: 'Reconciliation' }
  ];
}
