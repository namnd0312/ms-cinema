# Phase 5: Angular Admin Dashboard

## Context Links
- [plan.md](./plan.md)
- [admin.routes.ts](../../cinema-frontend/src/app/features/admin/admin.routes.ts)
- [admin-nav.component.ts](../../cinema-frontend/src/app/features/admin/admin-nav/admin-nav.component.ts)
- [payment.service.ts](../../cinema-frontend/src/app/core/services/payment.service.ts)
- [payment.model.ts](../../cinema-frontend/src/app/core/models/payment.model.ts)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add reconciliation section to Angular admin panel with dashboard, detail view, and CSV export

## Key Insights
- Follow existing patterns: standalone components, Material imports, lazy loading
- Existing admin nav uses `mat-tab-nav-bar` with router links
- Services use `inject(HttpClient)` pattern
- Models are TypeScript interfaces
- Keep each component file <200 lines

## Requirements
### Functional
- Dashboard: summary cards, trigger button with date picker, run history table
- Detail: items table with filtering, resolve action, CSV export
- Navigation: new "Reconciliation" tab in admin nav

### Non-functional
- Lazy loaded route
- Responsive Material design
- Paginated tables using `MatPaginator`

## Architecture
```
admin/
  reconciliation/
    reconciliation-dashboard.component.ts   - main view
    reconciliation-detail.component.ts      - run detail
  admin-nav/ (modify)
  admin.routes.ts (modify)

core/
  services/reconciliation.service.ts
  models/reconciliation.model.ts
```

## Related Code Files
### Create
- `core/models/reconciliation.model.ts` (~40 lines)
- `core/services/reconciliation.service.ts` (~50 lines)
- `features/admin/reconciliation/reconciliation-dashboard.component.ts` (~180 lines)
- `features/admin/reconciliation/reconciliation-detail.component.ts` (~180 lines)

### Modify
- `features/admin/admin.routes.ts` - add reconciliation routes
- `features/admin/admin-nav/admin-nav.component.ts` - add nav link

## Implementation Steps

### 1. reconciliation.model.ts (~40 lines)
```typescript
export interface ReconciliationRun {
  id: number;
  startDate: string;
  endDate: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  totalStripeRecords: number;
  totalLocalRecords: number;
  matchedCount: number;
  mismatchedCount: number;
  missingLocalCount: number;
  missingStripeCount: number;
  createdAt: string;
  completedAt: string | null;
}

export interface ReconciliationItem {
  id: number;
  runId: number;
  stripePaymentIntentId: string | null;
  localPaymentId: number | null;
  discrepancyType: DiscrepancyType;
  stripeAmount: number | null;
  localAmount: number | null;
  stripeStatus: string | null;
  localStatus: string | null;
  resolved: boolean;
  notes: string | null;
  createdAt: string;
}

export type DiscrepancyType = 'MATCHED' | 'STATUS_MISMATCH' | 'AMOUNT_MISMATCH' | 'MISSING_LOCAL' | 'MISSING_STRIPE';

export interface ReconciliationSummary {
  latestRunId: number;
  startDate: string;
  endDate: string;
  status: string;
  matched: number;
  mismatched: number;
  missingLocal: number;
  missingStripe: number;
  completedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
```

### 2. reconciliation.service.ts (~50 lines)
```typescript
@Injectable({ providedIn: 'root' })
export class ReconciliationService {
  private http = inject(HttpClient);
  private base = '/api/payments/reconciliation';

  triggerReconciliation(startDate: string, endDate: string) {
    return this.http.post<ReconciliationRun>(`${this.base}/trigger`, { startDate, endDate });
  }

  getRuns(page = 0, size = 10) {
    return this.http.get<PageResponse<ReconciliationRun>>(`${this.base}/runs`, {
      params: { page, size }
    });
  }

  getRunDetails(runId: number) {
    return this.http.get<ReconciliationRun>(`${this.base}/runs/${runId}`);
  }

  getRunItems(runId: number, page = 0, size = 20, discrepancyType?: DiscrepancyType) {
    let params: any = { page, size };
    if (discrepancyType) params.discrepancyType = discrepancyType;
    return this.http.get<PageResponse<ReconciliationItem>>(`${this.base}/runs/${runId}/items`, { params });
  }

  getSummary() {
    return this.http.get<ReconciliationSummary>(`${this.base}/summary`);
  }

  resolveItem(itemId: number, notes: string) {
    return this.http.put<ReconciliationItem>(`${this.base}/items/${itemId}/resolve`, { notes });
  }
}
```

### 3. reconciliation-dashboard.component.ts (~180 lines)

**Template sections:**
- **Summary cards** (4 cards): Matched, Mismatched, Missing Local, Missing Stripe
  - Use `mat-card` with colored headers
  - Load from `getSummary()` on init
- **Trigger section**:
  - `mat-date-range-input` for start/end date
  - "Run Reconciliation" `mat-raised-button`
  - Loading spinner during execution
- **Run history table**:
  - `mat-table` with columns: ID, Date Range, Status, Matched, Mismatched, Missing, Created At
  - Status chip colored by status (green=COMPLETED, red=FAILED, yellow=RUNNING)
  - Row click navigates to detail view
  - `mat-paginator` at bottom

**Component logic:**
- OnInit: load summary + first page of runs
- triggerReconciliation(): call service, refresh runs on completion
- viewRun(id): `router.navigate(['admin', 'reconciliation', id])`

### 4. reconciliation-detail.component.ts (~180 lines)

**Template sections:**
- **Run header**: date range, status, counts summary bar
- **Filter bar**: `mat-button-toggle-group` for discrepancy types (ALL, MATCHED, STATUS_MISMATCH, etc.)
- **Items table**:
  - `mat-table` columns: Stripe PI ID, Local ID, Type, Stripe Amount, Local Amount, Stripe Status, Local Status, Resolved, Actions
  - Discrepancy type shown as colored chip
  - Resolve button opens `mat-dialog` or inline textarea for notes
  - `mat-paginator`
- **Export button**: "Export CSV" downloads items

**Component logic:**
- Route param: `runId` from `ActivatedRoute`
- OnInit: load run details + first page of items
- filterByType(type): reload items with filter
- resolveItem(id): open dialog, call service, refresh row
- exportCsv(): fetch all items (unpaginated or iterate pages), generate CSV blob, trigger download

**CSV export implementation:**
```typescript
exportCsv() {
  // Fetch all items for the run
  this.reconciliationService.getRunItems(this.runId, 0, 10000).subscribe(page => {
    const headers = 'Stripe PI ID,Local ID,Type,Stripe Amount,Local Amount,Stripe Status,Local Status,Resolved,Notes\n';
    const rows = page.content.map(item =>
      `${item.stripePaymentIntentId},${item.localPaymentId},${item.discrepancyType},${item.stripeAmount},${item.localAmount},${item.stripeStatus},${item.localStatus},${item.resolved},${item.notes || ''}`
    ).join('\n');
    const blob = new Blob([headers + rows], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `reconciliation-run-${this.runId}.csv`;
    a.click();
  });
}
```

### 5. Update admin.routes.ts
Add two new routes inside children array:
```typescript
{
  path: 'reconciliation',
  loadComponent: () => import('./reconciliation/reconciliation-dashboard.component')
    .then(m => m.ReconciliationDashboardComponent)
},
{
  path: 'reconciliation/:runId',
  loadComponent: () => import('./reconciliation/reconciliation-detail.component')
    .then(m => m.ReconciliationDetailComponent)
}
```

### 6. Update admin-nav.component.ts
Add to `links` array:
```typescript
{ path: 'reconciliation', label: 'Reconciliation' }
```

## Todo List
- [ ] Create reconciliation.model.ts
- [ ] Create reconciliation.service.ts
- [ ] Create reconciliation-dashboard.component.ts
- [ ] Create reconciliation-detail.component.ts
- [ ] Update admin.routes.ts with new routes
- [ ] Update admin-nav.component.ts with nav link
- [ ] Verify `ng build` succeeds
- [ ] Manual test: trigger, view runs, view items, resolve, export CSV

## Success Criteria
- Reconciliation tab visible in admin nav
- Dashboard shows summary cards and run history
- Trigger button launches reconciliation with date range
- Detail view shows items filtered by type
- Resolve action marks items resolved with notes
- CSV export downloads correctly

## Risk Assessment
- **Low:** Material components well-documented and used elsewhere in project
- **Low:** Route naming consistent with existing admin routes
- **Medium:** Large item tables need pagination (implemented via MatPaginator)

## Security Considerations
- All API calls go through authenticated HttpClient (JWT interceptor)
- Routes protected by `adminGuard` (already configured)
- No sensitive data displayed (payment amounts and statuses only)

## Next Steps
- Phase 6: Testing
