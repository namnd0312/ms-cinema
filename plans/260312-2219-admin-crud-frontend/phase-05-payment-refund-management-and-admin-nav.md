# Phase 5: Payment Refund Management & Admin Navigation

## Context Links
- [plan.md](./plan.md)
- [Phase 1: Services & Models](./phase-01-admin-services-and-models.md)
- [payment.service.ts](../../cinema-frontend/src/app/core/services/payment.service.ts)
- [payment.model.ts](../../cinema-frontend/src/app/core/models/payment.model.ts)
- [PaymentController.java](../../payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java)
- [toolbar.component.ts](../../cinema-frontend/src/app/shared/components/toolbar/toolbar.component.ts)
- [admin.routes.ts](../../cinema-frontend/src/app/features/admin/admin.routes.ts)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Create PaymentManagementComponent for admin to list all payments and issue refunds. Add admin sub-navigation tabs for all 4 management pages. **BACKEND GAP:** No `GET /api/payments` list-all endpoint exists.

## Key Insights
- Backend `PaymentController` only has: `GET /api/payments/{id}` (owner), `GET /api/payments/my` (user), `POST /api/payments/{id}/refund` (admin)
- **No admin list-all endpoint exists** — must be added to backend first or this phase partially blocked
- `PaymentService.refundPayment(id)` already exists in frontend
- Payment statuses: PENDING, COMPLETED, FAILED, REFUNDED — only COMPLETED can be refunded
- Admin needs sub-nav (tabs or links) to switch between movies/theaters/showtimes/payments

## Requirements

### Functional
- PaymentManagementComponent listing all payments in MatTable (id, booking, user, amount, status, date, actions)
- Refund button on COMPLETED payments only
- `window.confirm()` before refund (KISS)
- MatSnackBar feedback
- Admin sub-navigation with links to all 4 pages (movies, theaters, showtimes, payments)
- Add `/admin/payments` route

### Non-functional
- Standalone component with signals
- Keep < 200 LOC
- Handle backend gap gracefully (error state or placeholder)

## Architecture

```
features/admin/
  payment-management/
    payment-management.component.ts     # NEW: payment list + refund action
  admin-nav/
    admin-nav.component.ts              # NEW: shared sub-nav tabs
```

Admin sub-nav pattern — simple inline nav bar component used inside each management page or as a layout wrapper:

```
/admin/movies    → [Movies] [Theaters] [Showtimes] [Payments]
/admin/theaters  → [Movies] [Theaters] [Showtimes] [Payments]
...
```

**Decision:** Use a layout component with `<router-outlet>` rather than embedding nav in each page (DRY).

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/admin/payment-management/payment-management.component.ts`
- `cinema-frontend/src/app/features/admin/admin-nav/admin-nav.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/admin/admin.routes.ts` — add payments route + layout wrapper
- `cinema-frontend/src/app/core/services/payment.service.ts` — add `getAllPayments()`

## Implementation Steps

### Step 1: Add getAllPayments to PaymentService (Phase 1 dependency)

Already planned in Phase 1. Add to `payment.service.ts`:

```typescript
getAllPayments(): Observable<Payment[]> {
  return this.http.get<Payment[]>('/api/payments');
}
```

### Step 2: Create AdminNavComponent

Shared sub-navigation for all admin pages. Uses `MatTabNav` for tab-style links.

```typescript
// admin-nav/admin-nav.component.ts
@Component({
  selector: 'app-admin-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="panel">
      @for (link of links; track link.path) {
        <a mat-tab-link [routerLink]="link.path" routerLinkActive #rla="routerLinkActive"
           [active]="rla.isActive">{{ link.label }}</a>
      }
    </nav>
    <mat-tab-nav-panel #panel>
      <router-outlet></router-outlet>
    </mat-tab-nav-panel>
  `,
  styles: [`
    nav { margin-bottom: 16px; }
  `]
})
export class AdminNavComponent {
  links = [
    { path: 'movies', label: 'Movies' },
    { path: 'theaters', label: 'Theaters' },
    { path: 'showtimes', label: 'Showtimes' },
    { path: 'payments', label: 'Payments' }
  ];
}
```

### Step 3: Create PaymentManagementComponent

```typescript
// payment-management/payment-management.component.ts
@Component({
  selector: 'app-payment-management',
  standalone: true,
  imports: [
    DatePipe, CurrencyPipe,
    MatTableModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatChipsModule
  ],
  template: `
    <div class="admin-container">
      <h1>Payment Management</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <table mat-table [dataSource]="payments()" class="full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let p">{{p.id}}</td></ng-container>
          <ng-container matColumnDef="bookingId">
            <th mat-header-cell *matHeaderCellDef>Booking</th>
            <td mat-cell *matCellDef="let p">#{{p.bookingId}}</td></ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let p">{{p.amount | currency}}</td></ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let p">
              <mat-chip [class]="'status-' + p.status.toLowerCase()">{{p.status}}</mat-chip>
            </td></ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let p">{{p.createdAt | date:'medium'}}</td></ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let p">
              @if (p.status === 'COMPLETED') {
                <button mat-flat-button color="warn" (click)="refund(p)">Refund</button>
              }
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1000px; margin: 0 auto; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
    .error { text-align: center; color: #f44336; padding: 32px; }
    .status-completed { background: #4caf50 !important; color: white; }
    .status-refunded { background: #ff9800 !important; color: white; }
    .status-failed { background: #f44336 !important; color: white; }
  `]
})
export class PaymentManagementComponent implements OnInit {
  private paymentService = inject(PaymentService);
  private snackBar = inject(MatSnackBar);
  payments = signal<Payment[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  columns = ['id', 'bookingId', 'amount', 'status', 'createdAt', 'actions'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.paymentService.getAllPayments().subscribe({
      next: (p) => { this.payments.set(p); this.loading.set(false); },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 404
          ? 'Admin payment listing not available. Backend endpoint required.'
          : 'Failed to load payments.');
      }
    });
  }

  refund(payment: Payment): void {
    if (!window.confirm(`Refund payment #${payment.id} ($${payment.amount})?`)) return;
    this.paymentService.refundPayment(payment.id).subscribe({
      next: () => { this.snackBar.open('Payment refunded', 'OK', { duration: 3000 }); this.load(); },
      error: () => this.snackBar.open('Refund failed', 'OK', { duration: 3000 })
    });
  }
}
```

### Step 4: Update admin.routes.ts

Restructure routes to use AdminNavComponent as layout with `<router-outlet>`:

```typescript
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    canActivate: [adminGuard],
    loadComponent: () => import('./admin-nav/admin-nav.component').then(m => m.AdminNavComponent),
    children: [
      { path: '', redirectTo: 'movies', pathMatch: 'full' },
      {
        path: 'movies',
        loadComponent: () => import('./movie-management/movie-management.component')
          .then(m => m.MovieManagementComponent)
      },
      {
        path: 'theaters',
        loadComponent: () => import('./theater-management/theater-management.component')
          .then(m => m.TheaterManagementComponent)
      },
      {
        path: 'showtimes',
        loadComponent: () => import('./showtime-management/showtime-management.component')
          .then(m => m.ShowtimeManagementComponent)
      },
      {
        path: 'payments',
        loadComponent: () => import('./payment-management/payment-management.component')
          .then(m => m.PaymentManagementComponent)
      }
    ]
  }
];
```

### Step 5: No toolbar changes needed

Toolbar already has "Admin" link at `/admin` with `hasRole('ROLE_ADMIN')` check. Sub-navigation handled by AdminNavComponent inside admin layout.

## Todo List
- [ ] Add `getAllPayments()` to `payment.service.ts` (if not done in Phase 1)
- [ ] Create `admin-nav.component.ts` with MatTabNav
- [ ] Create `payment-management.component.ts` with refund action
- [ ] Update `admin.routes.ts` — add layout wrapper + payments route
- [ ] Verify compile: `ng build`
- [ ] Test: nav tabs switch pages, payment list loads (or shows backend gap error), refund works

## Success Criteria
- Admin sub-nav tabs visible on all admin pages
- Tab switching works via router (no page reload)
- Payment list shows in MatTable (or graceful error if backend missing)
- Refund button only on COMPLETED payments
- Refund with confirm → success → list refreshes with REFUNDED status
- All components < 200 LOC

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| **BACKEND GAP: No `GET /api/payments` endpoint** | **HIGH** | Show error message in UI; add backend endpoint (simple `@GetMapping @PreAuthorize("hasRole('ADMIN')")` returning `paymentService.findAll()`). **This is a blocking dependency for full functionality.** |
| MatTabNav routing integration | Low | Standard Angular Material pattern, well-documented |
| AdminNavComponent adds layout wrapper to route tree | Low | Children render via `<router-outlet>`, no breaking change |
| Large payment list performance | Medium | Defer pagination to future (YAGNI); MVP loads all |

### Backend Fix Required

To unblock this phase, add to `PaymentController.java`:

```java
@Operation(summary = "List all payments (ADMIN only)")
@GetMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<PaymentHistoryResponse>> getAllPayments() {
    return ResponseEntity.ok(paymentService.getAllPayments());
}
```

And add `getAllPayments()` to `PaymentService` interface + impl (query all payments).

## Security Considerations
- Route protected by `adminGuard`
- Backend `refund` endpoint enforces `@PreAuthorize("hasRole('ADMIN')")`
- New `GET /api/payments` endpoint MUST also have `@PreAuthorize("hasRole('ADMIN')")` — regular users should NOT see all payments
- Payment data contains userId — admin-only visibility is critical

## Next Steps
- After all 5 phases: run `ng build` for full compile check
- Add backend `GET /api/payments` admin endpoint (separate backend task)
- Consider pagination for payments if list grows large (future)
