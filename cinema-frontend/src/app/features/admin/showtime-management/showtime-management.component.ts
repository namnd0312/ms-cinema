import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MovieService } from '../../../core/services/movie.service';
import { TheaterService } from '../../../core/services/theater.service';
import { Movie, Theater, Showtime } from '../../../core/models/movie.model';
import { ShowtimeFormDialogComponent, ShowtimeDialogData } from './showtime-form-dialog.component';

@Component({
  selector: 'app-showtime-management',
  standalone: true,
  imports: [
    DatePipe, CurrencyPipe,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="admin-container">
      <div class="header">
        <h1>Showtime Management</h1>
        <button mat-flat-button color="primary" (click)="openForm()" [disabled]="!dropdownsLoaded()">
          <mat-icon>add</mat-icon> Add Showtime
        </button>
      </div>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        <table mat-table [dataSource]="showtimes()" class="full-width">
          <ng-container matColumnDef="movie">
            <th mat-header-cell *matHeaderCellDef>Movie</th>
            <td mat-cell *matCellDef="let s">{{s.movie.title}}</td></ng-container>
          <ng-container matColumnDef="theater">
            <th mat-header-cell *matHeaderCellDef>Theater</th>
            <td mat-cell *matCellDef="let s">{{s.theater.name}}</td></ng-container>
          <ng-container matColumnDef="startTime">
            <th mat-header-cell *matHeaderCellDef>Start</th>
            <td mat-cell *matCellDef="let s">{{s.startTime | date:'MMM d, y h:mm a'}}</td></ng-container>
          <ng-container matColumnDef="basePrice">
            <th mat-header-cell *matHeaderCellDef>Price</th>
            <td mat-cell *matCellDef="let s">{{s.basePrice | currency}}</td></ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let s">
              <button mat-icon-button (click)="openForm(s)"><mat-icon>edit</mat-icon></button>
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
  `]
})
export class ShowtimeManagementComponent implements OnInit {
  private movieService = inject(MovieService);
  private theaterService = inject(TheaterService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  showtimes = signal<Showtime[]>([]);
  movies = signal<Movie[]>([]);
  theaters = signal<Theater[]>([]);
  loading = signal(true);
  dropdownsLoaded = signal(false);
  columns = ['movie', 'theater', 'startTime', 'basePrice', 'actions'];

  ngOnInit(): void {
    this.load();
    forkJoin([
      this.movieService.getMovies(),
      this.theaterService.getTheaters()
    ]).subscribe({
      next: ([m, t]) => {
        this.movies.set(m);
        this.theaters.set(t);
        this.dropdownsLoaded.set(true);
      },
      error: () => this.snackBar.open('Failed to load dropdown data', 'OK', { duration: 3000 })
    });
  }

  load(): void {
    this.movieService.getShowtimes().subscribe({
      next: (s) => { this.showtimes.set(s); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  openForm(showtime?: Showtime): void {
    const dialogData: ShowtimeDialogData = {
      showtime: showtime ?? null,
      movies: this.movies(),
      theaters: this.theaters()
    };
    this.dialog.open(ShowtimeFormDialogComponent, { width: '500px', data: dialogData })
      .afterClosed().subscribe(result => {
        if (result) {
          this.snackBar.open('Showtime saved', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }
}
