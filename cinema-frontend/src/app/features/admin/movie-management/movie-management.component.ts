import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MovieService } from '../../../core/services/movie.service';
import { Movie } from '../../../core/models/movie.model';
import { MovieFormDialogComponent } from './movie-form-dialog.component';

@Component({
  selector: 'app-movie-management',
  standalone: true,
  imports: [
    MatTableModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatFormFieldModule, MatInputModule,
    DatePipe
  ],
  template: `
    <div class="admin-container">
      <div class="header">
        <h1>Movie Management</h1>
        <button mat-flat-button color="primary" (click)="openForm()">
          <mat-icon>add</mat-icon> Add Movie
        </button>
      </div>
      <mat-form-field class="filter">
        <mat-label>Filter</mat-label>
        <input matInput (input)="applyFilter($event)" placeholder="Search by title...">
      </mat-form-field>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        <table mat-table [dataSource]="dataSource" class="full-width">
          <ng-container matColumnDef="title"><th mat-header-cell *matHeaderCellDef>Title</th>
            <td mat-cell *matCellDef="let m">{{m.title}}</td></ng-container>
          <ng-container matColumnDef="genre"><th mat-header-cell *matHeaderCellDef>Genre</th>
            <td mat-cell *matCellDef="let m">{{m.genre}}</td></ng-container>
          <ng-container matColumnDef="durationMinutes"><th mat-header-cell *matHeaderCellDef>Duration</th>
            <td mat-cell *matCellDef="let m">{{m.durationMinutes}} min</td></ng-container>
          <ng-container matColumnDef="releaseDate"><th mat-header-cell *matHeaderCellDef>Release</th>
            <td mat-cell *matCellDef="let m">{{m.releaseDate | date:'mediumDate'}}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let m">
              <button mat-icon-button (click)="openForm(m)"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button color="warn" (click)="delete(m)"><mat-icon>delete</mat-icon></button>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1000px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
    .filter { width: 100%; margin-bottom: 8px; }
  `]
})
export class MovieManagementComponent implements OnInit {
  private movieService = inject(MovieService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  loading = signal(true);
  dataSource = new MatTableDataSource<Movie>([]);
  columns = ['title', 'genre', 'durationMinutes', 'releaseDate', 'actions'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.movieService.getMovies().subscribe({
      next: (m) => { this.dataSource.data = m; this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  applyFilter(event: Event): void {
    this.dataSource.filter = (event.target as HTMLInputElement).value.trim().toLowerCase();
  }

  openForm(movie?: Movie): void {
    this.dialog.open(MovieFormDialogComponent, { width: '500px', data: movie ?? null })
      .afterClosed().subscribe(result => { if (result) this.load(); });
  }

  delete(movie: Movie): void {
    if (!window.confirm(`Delete "${movie.title}"?`)) return;
    this.movieService.deleteMovie(movie.id).subscribe({
      next: () => { this.snackBar.open('Movie deleted', 'OK', { duration: 3000 }); this.load(); },
      error: () => this.snackBar.open('Failed to delete movie', 'OK', { duration: 3000 })
    });
  }
}
