import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { TheaterService } from '../../../core/services/theater.service';
import { Theater, CreateTheaterRequest } from '../../../core/models/movie.model';

@Component({
  selector: 'app-theater-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edit Theater' : 'Add Theater' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid">
        <mat-form-field><mat-label>Name</mat-label>
          <input matInput formControlName="name"></mat-form-field>
        <mat-form-field><mat-label>Location</mat-label>
          <input matInput formControlName="location"></mat-form-field>
        <mat-form-field><mat-label>Total Rows</mat-label>
          <input matInput type="number" formControlName="totalRows"></mat-form-field>
        <mat-form-field><mat-label>Total Columns</mat-label>
          <input matInput type="number" formControlName="totalColumns"></mat-form-field>
      </form>
      @if (data) {
        <p class="hint">Note: changing rows/columns may regenerate the seat grid.</p>
      }
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
    .hint { color: rgba(255,255,255,0.6); font-size: 12px; margin-top: 8px; }
  `]
})
export class TheaterFormDialogComponent {
  private theaterService = inject(TheaterService);
  private dialogRef = inject(MatDialogRef<TheaterFormDialogComponent>);
  data: Theater | null = inject(MAT_DIALOG_DATA);
  saving = signal(false);

  form = inject(FormBuilder).group({
    name: [this.data?.name ?? '', Validators.required],
    location: [this.data?.location ?? '', Validators.required],
    totalRows: [this.data?.totalRows ?? null, [Validators.required, Validators.min(1)]],
    totalColumns: [this.data?.totalColumns ?? null, [Validators.required, Validators.min(1)]]
  });

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const request = this.form.getRawValue() as CreateTheaterRequest;
    const op = this.data
      ? this.theaterService.updateTheater(this.data.id, request)
      : this.theaterService.createTheater(request);
    op.subscribe({
      next: (theater) => this.dialogRef.close(theater),
      error: () => this.saving.set(false)
    });
  }
}
