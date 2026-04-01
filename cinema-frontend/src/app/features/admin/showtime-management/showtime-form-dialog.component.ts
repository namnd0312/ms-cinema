import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ShowtimeAdminService } from '../../../core/services/showtime-admin.service';
import { combineDatetime, parseTime } from '../../../core/utils/date-format.util';
import { Movie, Theater, Showtime, CreateShowtimeRequest } from '../../../core/models/movie.model';

export interface ShowtimeDialogData {
  showtime: Showtime | null;
  movies: Movie[];
  theaters: Theater[];
}

@Component({
  selector: 'app-showtime-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule,
    MatDatepickerModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.showtime ? 'Edit Showtime' : 'Add Showtime' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid">
        <mat-form-field><mat-label>Movie</mat-label>
          <mat-select formControlName="movieId">
            @for (m of data.movies; track m.id) {
              <mat-option [value]="m.id">{{m.title}}</mat-option>
            }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Theater</mat-label>
          <mat-select formControlName="theaterId">
            @for (t of data.theaters; track t.id) {
              <mat-option [value]="t.id">{{t.name}} — {{t.location}}</mat-option>
            }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Start Date</mat-label>
          <input matInput [matDatepicker]="startDp" formControlName="startDate">
          <mat-datepicker-toggle matSuffix [for]="startDp"></mat-datepicker-toggle>
          <mat-datepicker #startDp></mat-datepicker></mat-form-field>
        <mat-form-field><mat-label>Start Time</mat-label>
          <input matInput type="time" formControlName="startTime"></mat-form-field>
        <mat-form-field><mat-label>End Date</mat-label>
          <input matInput [matDatepicker]="endDp" formControlName="endDate">
          <mat-datepicker-toggle matSuffix [for]="endDp"></mat-datepicker-toggle>
          <mat-datepicker #endDp></mat-datepicker></mat-form-field>
        <mat-form-field><mat-label>End Time</mat-label>
          <input matInput type="time" formControlName="endTime"></mat-form-field>
        <mat-form-field><mat-label>Base Price</mat-label>
          <input matInput type="number" step="0.01" formControlName="basePrice">
          <span matPrefix>$&nbsp;</span>
        </mat-form-field>
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
  `]
})
export class ShowtimeFormDialogComponent {
  private showtimeAdmin = inject(ShowtimeAdminService);
  private dialogRef = inject(MatDialogRef<ShowtimeFormDialogComponent>);
  data: ShowtimeDialogData = inject(MAT_DIALOG_DATA);
  saving = signal(false);

  form = inject(FormBuilder).group({
    movieId: [this.data.showtime?.movie.id ?? null, Validators.required],
    theaterId: [this.data.showtime?.theater.id ?? null, Validators.required],
    startDate: [this.parseDate(this.data.showtime?.startTime), Validators.required],
    startTime: [this.data.showtime?.startTime ? parseTime(this.data.showtime.startTime) : '', Validators.required],
    endDate: [this.parseDate(this.data.showtime?.endTime), Validators.required],
    endTime: [this.data.showtime?.endTime ? parseTime(this.data.showtime.endTime) : '', Validators.required],
    basePrice: [this.data.showtime?.basePrice ?? null, [Validators.required, Validators.min(0)]]
  });

  private parseDate(iso?: string): Date | null {
    return iso ? new Date(iso) : null;
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const val = this.form.getRawValue();
    const request: CreateShowtimeRequest = {
      movieId: val.movieId!,
      theaterId: val.theaterId!,
      startTime: combineDatetime(val.startDate!, val.startTime!),
      endTime: combineDatetime(val.endDate!, val.endTime!),
      basePrice: val.basePrice!
    };
    const op = this.data.showtime
      ? this.showtimeAdmin.updateShowtime(this.data.showtime.id, request)
      : this.showtimeAdmin.createShowtime(request);
    op.subscribe({
      next: (st) => this.dialogRef.close(st),
      error: () => this.saving.set(false)
    });
  }
}
