# Phase 2: Movie Management Page & Dialog

## Context Links
- [plan.md](./plan.md)
- [Phase 1: Services & Models](./phase-01-admin-services-and-models.md)
- [movie.service.ts](../../cinema-frontend/src/app/core/services/movie.service.ts)
- [admin.routes.ts](../../cinema-frontend/src/app/features/admin/admin.routes.ts)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Create MovieManagementComponent with MatTable listing + MovieFormDialogComponent for create/edit. Add `/admin/movies` route.

## Key Insights
- MovieService already has all CRUD methods (`createMovie`, `updateMovie`, `deleteMovie`) — just wire UI
- Backend `CreateMovieRequest` fields: title, description, genre, durationMin, rating (String), posterUrl, releaseDate
- Frontend `Movie` model has `durationMinutes` but backend request uses `durationMin` — map in form
- Use `window.confirm()` for delete (KISS, no custom confirm dialog)
- First MatDialog usage in codebase — establishes pattern for Phases 3-4

## Requirements

### Functional
- List all movies in MatTable with columns: title, genre, duration, release date, rating, actions
- "Add Movie" button opens MatDialog with empty form
- Edit button opens MatDialog pre-filled with movie data
- Delete button with `window.confirm()` then calls `deleteMovie`
- Success/error feedback via MatSnackBar
- Search/filter by title (client-side MatTable filter)

### Non-functional
- Inline template/styles (codebase convention)
- Each component < 200 LOC (split into management + dialog)
- Standalone components with signals

## Architecture

```
features/admin/
  movie-management/
    movie-management.component.ts       # NEW: MatTable list + actions
    movie-form-dialog.component.ts      # NEW: MatDialog create/edit form
```

MatDialog data flow:
```
MovieManagement → dialog.open(MovieFormDialog, { data: movie | null })
                ← afterClosed() → result: Movie | undefined
                → refresh list on result
```

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/admin/movie-management/movie-management.component.ts`
- `cinema-frontend/src/app/features/admin/movie-management/movie-form-dialog.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/admin/admin.routes.ts` — add movies route

## Implementation Steps

### Step 1: Create MovieFormDialogComponent

Dialog for create/edit movie. Receives `Movie | null` via `MAT_DIALOG_DATA`. Returns saved `Movie` on success.

```typescript
// movie-form-dialog.component.ts
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
  `
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
    // Format releaseDate to ISO date string
    const request = { ...val, releaseDate: val.releaseDate?.toISOString().split('T')[0] ?? null };
    const op = this.data
      ? this.movieService.updateMovie(this.data.id, request)
      : this.movieService.createMovie(request);
    op.subscribe({
      next: (movie) => this.dialogRef.close(movie),
      error: () => this.saving.set(false)
    });
  }
}
```

### Step 2: Create MovieManagementComponent

MatTable list with add/edit/delete actions.

```typescript
// movie-management.component.ts
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
  `
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
```

### Step 3: Add route to admin.routes.ts

```typescript
// Add to children array in admin.routes.ts
{
  path: 'movies',
  loadComponent: () => import('./movie-management/movie-management.component')
    .then(m => m.MovieManagementComponent)
}
```

Update default redirect from `'theaters'` to `'movies'`.

## Todo List
- [ ] Create `movie-form-dialog.component.ts` with reactive form
- [ ] Create `movie-management.component.ts` with MatTable
- [ ] Add `/admin/movies` route to `admin.routes.ts`
- [ ] Update default redirect to `'movies'`
- [ ] Verify compile: `ng build`
- [ ] Test: open dialog, fill form, save, edit, delete

## Success Criteria
- Movie list displays in MatTable with filter
- Add movie opens empty dialog, saves, list refreshes
- Edit movie opens pre-filled dialog, saves, list refreshes
- Delete shows confirm, removes movie, list refreshes
- Snackbar shows on success/error
- Both components < 200 LOC

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| `durationMin` vs `durationMinutes` mismatch | Low | Map in form: send `durationMin`, receive `durationMinutes` |
| MatDatepicker not imported in app | Medium | Ensure `MatNativeDateModule` is in dialog imports |
| Movie rating is String not number | Low | Use MatSelect with predefined values |

## Security Considerations
- Route protected by `adminGuard`
- Backend enforces `@PreAuthorize("hasRole('ADMIN')")` on POST/PUT/DELETE
- No sensitive data in movie forms
