import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MovieService } from '../../../core/services/movie.service';
import { Movie, CreateMovieRequest } from '../../../core/models/movie.model';

@Component({
  selector: 'app-movie-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatDatepickerModule,
    MatNativeDateModule, MatSelectModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edit Movie' : 'Add Movie' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid">
        <mat-form-field><mat-label>Title</mat-label>
          <input matInput formControlName="title"></mat-form-field>
        <mat-form-field><mat-label>Genre</mat-label>
          <input matInput formControlName="genre"></mat-form-field>
        <mat-form-field><mat-label>Duration (min)</mat-label>
          <input matInput type="number" formControlName="durationMin"></mat-form-field>
        <mat-form-field><mat-label>Rating</mat-label>
          <mat-select formControlName="rating">
            @for (r of ratings; track r) { <mat-option [value]="r">{{r}}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Release Date</mat-label>
          <input matInput [matDatepicker]="dp" formControlName="releaseDate">
          <mat-datepicker-toggle matSuffix [for]="dp"></mat-datepicker-toggle>
          <mat-datepicker #dp></mat-datepicker></mat-form-field>
        <mat-form-field><mat-label>Poster URL</mat-label>
          <input matInput formControlName="posterUrl"></mat-form-field>
        <mat-form-field class="full-width"><mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="form.invalid || saving()">
        {{ saving() ? 'Saving...' : 'Save' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-grid { display: flex; flex-wrap: wrap; gap: 0 16px; }
    .form-grid mat-form-field { flex: 1 1 45%; min-width: 200px; }
    .form-grid .full-width { flex: 1 1 100%; }
  `]
})
export class MovieFormDialogComponent {
  private movieService = inject(MovieService);
  private dialogRef = inject(MatDialogRef<MovieFormDialogComponent>);
  data: Movie | null = inject(MAT_DIALOG_DATA);
  saving = signal(false);
  ratings = ['G', 'PG', 'PG-13', 'R', 'NC-17'];

  form = inject(FormBuilder).group({
    title: [this.data?.title ?? '', Validators.required],
    description: [this.data?.description ?? ''],
    genre: [this.data?.genre ?? ''],
    durationMin: [this.data?.durationMinutes ?? null, Validators.required],
    rating: [this.data?.rating ?? ''],
    posterUrl: [this.data?.posterUrl ?? ''],
    releaseDate: [this.data?.releaseDate ? new Date(this.data.releaseDate) : null]
  });

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const val = this.form.getRawValue();
    const request = {
      title: val.title!,
      description: val.description ?? '',
      genre: val.genre ?? '',
      durationMin: val.durationMin!,
      rating: val.rating ?? '',
      posterUrl: val.posterUrl ?? '',
      releaseDate: val.releaseDate?.toISOString().split('T')[0] ?? ''
    } as CreateMovieRequest;
    const op = this.data
      ? this.movieService.updateMovie(this.data.id, request)
      : this.movieService.createMovie(request);
    op.subscribe({
      next: (movie) => this.dialogRef.close(movie),
      error: () => this.saving.set(false)
    });
  }
}
