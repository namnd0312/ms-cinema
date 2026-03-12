# Phase 3: Theater Management Enhancement & Dialog

## Context Links
- [plan.md](./plan.md)
- [Phase 1: Services & Models](./phase-01-admin-services-and-models.md)
- [theater-management.component.ts](../../cinema-frontend/src/app/features/admin/theater-management/theater-management.component.ts)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Enhance existing TheaterManagementComponent from read-only card list to full CRUD with MatTable. Create TheaterFormDialogComponent for create/edit. Replace raw HttpClient with TheaterService.

## Key Insights
- Existing component is ~50 LOC, uses raw `HttpClient` for GET only
- Backend has no DELETE theater endpoint — only create + update
- Backend TheaterDto returns: id, name, location, totalRows, totalColumns, createdAt
- Creating a theater auto-generates seat grid on backend (totalRows x totalColumns)
- Existing component shows `theater.totalSeats` — may need to compute from totalRows * totalColumns or keep backward compat

## Requirements

### Functional
- Replace mat-card list with MatTable (name, location, rows, columns, seats, actions)
- "Add Theater" button opens MatDialog with empty form
- Edit button opens MatDialog pre-filled
- No delete (backend has no DELETE endpoint)
- MatSnackBar feedback on success/error

### Non-functional
- Reuse existing component file (update in place, no new enhanced file)
- Use TheaterService from Phase 1 instead of raw HttpClient
- Keep < 200 LOC

## Architecture

```
features/admin/
  theater-management/
    theater-management.component.ts       # MODIFY: mat-card → MatTable + actions
    theater-form-dialog.component.ts      # NEW: MatDialog create/edit form
```

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/admin/theater-management/theater-form-dialog.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/admin/theater-management/theater-management.component.ts`

## Implementation Steps

### Step 1: Create TheaterFormDialogComponent

```typescript
// theater-form-dialog.component.ts
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
  `
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
```

### Step 2: Rewrite TheaterManagementComponent

Replace entire content — switch from mat-card + HttpClient to MatTable + TheaterService + MatDialog.

```typescript
// theater-management.component.ts — full rewrite
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
```

## Todo List
- [ ] Create `theater-form-dialog.component.ts`
- [ ] Rewrite `theater-management.component.ts` (mat-card → MatTable + TheaterService)
- [ ] Verify compile: `ng build`
- [ ] Test: list loads, add theater, edit theater, snackbar feedback

## Success Criteria
- Theater list in MatTable with name, location, size columns
- Add theater opens dialog, saves, list refreshes
- Edit theater opens pre-filled dialog, saves, list refreshes
- No raw HttpClient usage — all through TheaterService
- Both components < 200 LOC

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Backend returns `totalSeats` not `totalRows*totalColumns` in old model | Low | Phase 1 updates model; `totalSeats` kept for compat |
| Seat grid regeneration on update | Medium | Show warning hint in edit dialog |
| No delete endpoint | None | Don't show delete button; document limitation |

## Security Considerations
- Route protected by `adminGuard`
- Backend enforces `@PreAuthorize("hasRole('ADMIN')")` on POST/PUT
- Theater create auto-generates seats — ensure admin understands grid implications
