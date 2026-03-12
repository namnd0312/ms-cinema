# Phase 4: Showtime Management Enhancement & Dialog

## Context Links
- [plan.md](./plan.md)
- [Phase 1: Services & Models](./phase-01-admin-services-and-models.md)
- [showtime-management.component.ts](../../cinema-frontend/src/app/features/admin/showtime-management/showtime-management.component.ts)
- [movie.service.ts](../../cinema-frontend/src/app/core/services/movie.service.ts)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Enhance existing ShowtimeManagementComponent from read-only card list to full CRUD with MatTable. Create ShowtimeFormDialogComponent with movie/theater dropdowns for create/edit.

## Key Insights
- Existing component is ~50 LOC, uses `MovieService.getShowtimes()` for read
- Backend has no DELETE showtime endpoint — only create + update
- Dialog needs movie + theater dropdown lists — must load both on open
- `CreateShowtimeRequest` uses `movieId`/`theaterId` (Long), not full objects
- Datetime fields need `datetime-local` input (no MatDatetimePicker in standard Angular Material)
- `basePrice` is BigDecimal on backend — number input with step="0.01" on frontend

## Requirements

### Functional
- Replace mat-card list with MatTable (movie, theater, start time, end time, price, actions)
- "Add Showtime" button opens MatDialog with dropdowns + datetime fields
- Edit button opens pre-filled dialog
- No delete (backend has no DELETE endpoint)
- Movie and theater dropdowns populated from existing services
- MatSnackBar feedback

### Non-functional
- Reuse existing component file (update in place)
- Use `MovieService.getShowtimes()` for reads, `ShowtimeAdminService` for create/update
- Use `MovieService.getMovies()` and `TheaterService.getTheaters()` for dropdown data
- Keep < 200 LOC per file

## Architecture

```
features/admin/
  showtime-management/
    showtime-management.component.ts       # MODIFY: mat-card → MatTable + actions
    showtime-form-dialog.component.ts      # NEW: MatDialog with dropdowns
```

Dialog data flow:
```
ShowtimeManagement → dialog.open(ShowtimeFormDialog, {
  data: { showtime: Showtime | null, movies: Movie[], theaters: Theater[] }
})
← afterClosed() → result: Showtime | undefined
```

**Important:** Pre-load movies + theaters before opening dialog to avoid loading inside dialog.

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/admin/showtime-management/showtime-form-dialog.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/admin/showtime-management/showtime-management.component.ts`

## Implementation Steps

### Step 1: Define dialog data interface

In the dialog component file, define a local interface:

```typescript
export interface ShowtimeDialogData {
  showtime: Showtime | null;
  movies: Movie[];
  theaters: Theater[];
}
```

### Step 2: Create ShowtimeFormDialogComponent

```typescript
// showtime-form-dialog.component.ts
@Component({
  selector: 'app-showtime-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule
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
        <mat-form-field><mat-label>Start Time</mat-label>
          <input matInput type="datetime-local" formControlName="startTime">
        </mat-form-field>
        <mat-form-field><mat-label>End Time</mat-label>
          <input matInput type="datetime-local" formControlName="endTime">
        </mat-form-field>
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
  `
})
export class ShowtimeFormDialogComponent {
  private showtimeAdmin = inject(ShowtimeAdminService);
  private dialogRef = inject(MatDialogRef<ShowtimeFormDialogComponent>);
  data: ShowtimeDialogData = inject(MAT_DIALOG_DATA);
  saving = signal(false);

  form = inject(FormBuilder).group({
    movieId: [this.data.showtime?.movie.id ?? null, Validators.required],
    theaterId: [this.data.showtime?.theater.id ?? null, Validators.required],
    startTime: [this.formatDatetime(this.data.showtime?.startTime), Validators.required],
    endTime: [this.formatDatetime(this.data.showtime?.endTime), Validators.required],
    basePrice: [this.data.showtime?.basePrice ?? null, [Validators.required, Validators.min(0)]]
  });

  // Convert ISO string to datetime-local format: "2026-03-15T14:30"
  private formatDatetime(iso?: string): string {
    if (!iso) return '';
    return iso.substring(0, 16); // "YYYY-MM-DDTHH:mm"
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const val = this.form.getRawValue();
    const request: CreateShowtimeRequest = {
      movieId: val.movieId!,
      theaterId: val.theaterId!,
      startTime: val.startTime!,
      endTime: val.endTime!,
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
```

### Step 3: Rewrite ShowtimeManagementComponent

Replace card list with MatTable. Pre-load movies + theaters via `forkJoin` for dialog dropdowns.

```typescript
// showtime-management.component.ts — full rewrite
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
    // Pre-load dropdowns in parallel
    forkJoin([
      this.movieService.getMovies(),
      this.theaterService.getTheaters()
    ]).subscribe(([m, t]) => {
      this.movies.set(m);
      this.theaters.set(t);
      this.dropdownsLoaded.set(true);
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
```

## Todo List
- [ ] Create `showtime-form-dialog.component.ts` with movie/theater dropdowns
- [ ] Rewrite `showtime-management.component.ts` (mat-card → MatTable + forkJoin dropdowns)
- [ ] Verify compile: `ng build`
- [ ] Test: list loads, add showtime with dropdowns, edit showtime, snackbar feedback

## Success Criteria
- Showtime list in MatTable with movie, theater, start time, price columns
- Add showtime dialog has movie + theater dropdowns populated
- Edit showtime dialog pre-selects correct movie/theater
- datetime-local inputs work for start/end times
- Both components < 200 LOC

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| datetime-local format mismatch with backend ISO | Medium | `substring(0,16)` for display; raw value sent as ISO to backend |
| Empty movies/theaters list blocks dialog | Low | Disable "Add" button until `dropdownsLoaded` is true |
| No delete endpoint | None | Don't show delete button |
| Large movie/theater lists slow dropdown | Low | Unlikely at admin scale; add MatAutocomplete later if needed (YAGNI) |

## Security Considerations
- Route protected by `adminGuard`
- Backend enforces `@PreAuthorize("hasRole('ADMIN')")` on POST/PUT
- Movie/theater IDs validated server-side (foreign key constraints)
