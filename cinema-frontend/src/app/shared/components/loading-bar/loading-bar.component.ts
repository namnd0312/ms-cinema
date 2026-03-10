import { Component, inject } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LoadingService } from '../../../core/services/loading.service';

@Component({
  selector: 'app-loading-bar',
  standalone: true,
  imports: [MatProgressBarModule],
  template: `
    @if (loadingService.loading()) {
      <mat-progress-bar mode="indeterminate" class="loading-bar"></mat-progress-bar>
    }
  `,
  styles: [`
    .loading-bar { position: fixed; top: 0; left: 0; right: 0; z-index: 2000; }
  `]
})
export class LoadingBarComponent {
  loadingService = inject(LoadingService);
}
