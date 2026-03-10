import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="not-found">
      <mat-icon class="icon">movie_filter</mat-icon>
      <h1>404</h1>
      <p>Page not found</p>
      <a mat-raised-button color="primary" routerLink="/movies">Go to Movies</a>
    </div>
  `,
  styles: [`
    .not-found { display: flex; flex-direction: column; align-items: center; justify-content: center;
      min-height: 60vh; text-align: center; }
    .icon { font-size: 80px; height: 80px; width: 80px; color: rgba(255,255,255,0.2); }
    h1 { font-size: 4rem; margin: 16px 0 8px; }
    p { color: rgba(255,255,255,0.5); margin-bottom: 24px; }
  `]
})
export class NotFoundComponent {}
