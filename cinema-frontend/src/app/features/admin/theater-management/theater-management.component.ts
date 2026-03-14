import { Component, inject, signal, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TheaterService } from '../../../core/services/theater.service';
import { Theater } from '../../../core/models/movie.model';
import { TheaterFormDialogComponent } from './theater-form-dialog.component';

@Component({
  selector: 'app-theater-management',
  standalone: true,
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="admin-container">
      <div class="header">
        <h1>Theater Management</h1>
        <button mat-flat-button color="primary" (click)="openForm()">
          <mat-icon>add</mat-icon> Add Theater
        </button>
      </div>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        <table mat-table [dataSource]="theaters()" class="full-width">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let t">{{t.name}}</td></ng-container>
          <ng-container matColumnDef="location">
            <th mat-header-cell *matHeaderCellDef>Location</th>
            <td mat-cell *matCellDef="let t">{{t.location}}</td></ng-container>
          <ng-container matColumnDef="size">
            <th mat-header-cell *matHeaderCellDef>Size</th>
            <td mat-cell *matCellDef="let t">{{t.totalRows}}x{{t.totalColumns}}</td></ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let t">
              <button mat-icon-button (click)="openForm(t)"><mat-icon>edit</mat-icon></button>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 900px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
  `]
})
export class TheaterManagementComponent implements OnInit {
  private theaterService = inject(TheaterService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  theaters = signal<Theater[]>([]);
  loading = signal(true);
  columns = ['name', 'location', 'size', 'actions'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.theaterService.getTheaters().subscribe({
      next: (t) => { this.theaters.set(t); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  openForm(theater?: Theater): void {
    this.dialog.open(TheaterFormDialogComponent, { width: '450px', data: theater ?? null })
      .afterClosed().subscribe(result => {
        if (result) {
          this.snackBar.open('Theater saved', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }
}
