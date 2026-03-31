# Research: Admin Reconciliation Dashboard Patterns (Angular 18 + Material)

## 1. Server-Side Data Table with Pagination, Sorting, Filtering

**Pattern:** Use CDK Data Source with MatSort, MatPaginator, and reactive event merging.

Standard MatTableDataSource handles only client-side operations. For server-side efficiency (critical for large reconciliation datasets):

```typescript
// Component
export class ReconciliationTableComponent implements OnInit, OnDestroy {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  isLoadingResults = false;
  displayedColumns = ['id', 'reference', 'amount', 'status', 'date'];

  ngOnInit() {
    merge(this.paginator.page, this.sort.sortChange, this.filterChange$)
      .pipe(
        startWith({}),
        tap(() => this.isLoadingResults = true),
        switchMap(() => this.reconciliationService.getReconciliations(
          this.paginator.pageIndex,
          this.paginator.pageSize,
          this.sort.active,
          this.sort.direction,
          this.filterValue
        ))
      )
      .subscribe(data => {
        this.isLoadingResults = false;
        this.dataSource.data = data.items;
        this.paginator.length = data.total;
      });
  }
}
```

**Key:** Events (sort, page, filter) trigger API calls. Progress spinner shows during fetch. Server returns total count for accurate pagination.

**Resources:**
- [Angular Material Data Table Complete Example](https://blog.angular-university.io/angular-material-data-table/)
- [Server-Side Filtering & Sorting Combined](https://medium.com/@sushaman/combined-api-sort-filtering-and-pagination-in-angular-material-table-b2fc3dd24fb3)

---

## 2. Date Range Picker for Period Selection

**Pattern:** Use mat-date-range-input with mat-date-range-picker; optionally add preset ranges.

```typescript
// Template
<mat-form-field appearance="outline">
  <mat-label>Reconciliation Period</mat-label>
  <mat-date-range-input [formGroup]="dateRangeForm" [rangePicker]="picker">
    <input matStartDate formControlName="start" placeholder="Start date">
    <input matEndDate formControlName="end" placeholder="End date">
  </mat-date-range-input>
  <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
  <mat-date-range-picker #picker></mat-date-range-picker>
</mat-form-field>

<!-- Preset Ranges (Optional) -->
<button mat-button (click)="setLast30Days()">Last 30 Days</button>
<button mat-button (click)="setThisMonth()">This Month</button>
```

**Constraints:** Use [min] and [max] to limit selectable range (e.g., no future dates). Listen to valueChanges and trigger table refresh.

**Resource:** [Angular Material Date Range Picker Guide](https://blog.angular-university.io/angular-material-datepicker/)

---

## 3. Summary Stats Cards Display

**Pattern:** Grid of Material cards showing KPIs above table.

```html
<mat-grid-list cols="4" rowHeight="120px" class="stats-grid">
  <mat-grid-tile>
    <mat-card class="stat-card">
      <mat-card-content>
        <div class="stat-value">{{ stats.totalMatched }}</div>
        <div class="stat-label">Matched</div>
      </mat-card-content>
    </mat-card>
  </mat-grid-tile>

  <mat-grid-tile>
    <mat-card class="stat-card error">
      <mat-card-content>
        <div class="stat-value">{{ stats.totalMismatched }}</div>
        <div class="stat-label">Mismatched</div>
      </mat-card-content>
    </mat-card>
  </mat-grid-tile>

  <mat-grid-tile>
    <mat-card class="stat-card warning">
      <mat-card-content>
        <div class="stat-value">{{ stats.totalMissing }}</div>
        <div class="stat-label">Missing</div>
      </mat-card-content>
    </mat-card>
  </mat-grid-tile>

  <mat-grid-tile>
    <mat-card class="stat-card">
      <mat-card-content>
        <div class="stat-value">{{ stats.successRate }}%</div>
        <div class="stat-label">Success Rate</div>
      </mat-card-content>
    </mat-card>
  </mat-grid-tile>
</mat-grid-list>
```

Stats refresh alongside table data fetch. Use color theming (error=red, warning=orange) for visual hierarchy.

---

## 4. CSV Export from Material Table

**Pattern:** Use `mat-table-exporter` library for minimal friction.

```bash
npm install mat-table-exporter
```

```typescript
// Component
import { MatTableExporterModule } from 'mat-table-exporter';

export class ReconciliationTableComponent {
  @ViewChild('exporter') exporter!: MatTableExporter;

  exportToCSV() {
    this.exporter.exportTable('csv', {
      fileName: `reconciliation-${this.dateRangeForm.value.start}-${this.dateRangeForm.value.end}.csv`,
      showLabels: true,
      showTitle: false
    });
  }
}
```

```html
<button mat-raised-button color="primary" (click)="exportToCSV()">
  <mat-icon>download</mat-icon>
  Export CSV
</button>

<mat-table [dataSource]="dataSource" matTableExporter #exporter="matTableExporter">
  <!-- columns -->
</mat-table>
```

**Caveat:** For paginated tables, only current page exports. Fetch all data server-side if full export needed.

**Resource:** [mat-table-exporter npm](https://www.npmjs.com/package/mat-table-exporter)

---

## 5. Admin-Only Route Guards + Role-Based Access

**Pattern:** CanActivate guard checks role before allowing route navigation.

```typescript
// admin.guard.ts
import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.hasRole('ADMIN')) {
    return true;
  }

  // Log unauthorized attempt
  console.warn(`Unauthorized access to ${state.url}`);
  return router.createUrlTree(['/unauthorized']);
};
```

```typescript
// app.routes.ts
const routes: Routes = [
  {
    path: 'admin/reconciliation',
    component: ReconciliationDashboardComponent,
    canActivate: [adminGuard],
    data: { allowedRoles: ['ADMIN'] }
  }
];
```

```typescript
// auth.service.ts
export class AuthService {
  private currentUser$ = new BehaviorSubject<User | null>(null);

  hasRole(role: string): boolean {
    const user = this.currentUser$.value;
    return user?.roles?.includes(role) ?? false;
  }
}
```

**Security Note:** Client-side guards prevent accidental navigation only. **ALWAYS validate roles server-side before returning reconciliation data.** Backend must check `Authorization` header and reject unauthorized requests.

**Resource:** [Angular Route Guards Official Guide](https://angular.dev/guide/routing/route-guards)

---

## Implementation Priority

1. **Route Guard** (authentication layer, blocks all unauthorized access)
2. **Date Range Picker** (period selection)
3. **Summary Stats** (quick KPI overview)
4. **Server-Side Table** (handles large datasets efficiently)
5. **CSV Export** (minimal impact, add last if time-constrained)

---

## Summary

Angular 18 + Material provides built-in components for most patterns. Key architectural choice: implement custom CDK Data Source for server-side operations to avoid loading entire reconciliation dataset into memory. Guards + roles prevent unauthorized access, but backend validation is critical. Libraries like mat-table-exporter simplify exports.

---

**Sources:**
- [Angular Material Data Table Complete Example](https://blog.angular-university.io/angular-material-data-table/)
- [Server-Side Filtering & Sorting Combined](https://medium.com/@sushaman/combined-api-sort-filtering-and-pagination-in-angular-material-table-b2fc3dd24fb3)
- [Angular Material Date Range Picker Guide](https://blog.angular-university.io/angular-material-datepicker/)
- [mat-table-exporter npm](https://www.npmjs.com/package/mat-table-exporter)
- [Angular Route Guards Official Guide](https://angular.dev/guide/routing/route-guards)
