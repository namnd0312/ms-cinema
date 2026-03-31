import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ReconciliationService } from '../../../core/services/reconciliation.service';
import { ReconciliationRun, ReconciliationItem, DiscrepancyType } from '../../../core/models/reconciliation.model';

@Component({
  selector: 'app-reconciliation-detail',
  standalone: true,
  imports: [
    DatePipe, RouterLink,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatChipsModule, MatButtonToggleModule, MatPaginatorModule
  ],
  template: `
    <div class="admin-container">
      <button mat-button routerLink="/admin/reconciliation"><mat-icon>arrow_back</mat-icon> Back</button>

      @if (run()) {
        <h1>Run #{{ run()!.id }}: {{ run()!.startDate }} — {{ run()!.endDate }}</h1>
        <div class="stats-bar">
          <mat-chip class="status-{{ run()!.status.toLowerCase() }}">{{ run()!.status }}</mat-chip>
          <span>Matched: {{ run()!.matchedCount }}</span>
          <span>Mismatched: {{ run()!.mismatchedCount }}</span>
          <span>Missing Local: {{ run()!.missingLocalCount }}</span>
          <span>Missing Stripe: {{ run()!.missingStripeCount }}</span>
        </div>
      }

      <!-- Filter -->
      <mat-button-toggle-group [value]="activeFilter()" (change)="filterByType($event.value)">
        <mat-button-toggle value="ALL">All</mat-button-toggle>
        <mat-button-toggle value="MATCHED">Matched</mat-button-toggle>
        <mat-button-toggle value="STATUS_MISMATCH">Status Mismatch</mat-button-toggle>
        <mat-button-toggle value="AMOUNT_MISMATCH">Amount Mismatch</mat-button-toggle>
        <mat-button-toggle value="MISSING_LOCAL">Missing Local</mat-button-toggle>
        <mat-button-toggle value="MISSING_STRIPE">Missing Stripe</mat-button-toggle>
      </mat-button-toggle-group>

      <button mat-raised-button (click)="exportCsv()" class="export-btn"><mat-icon>download</mat-icon> Export CSV</button>

      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        <table mat-table [dataSource]="items()" class="full-width">
          <ng-container matColumnDef="stripeId">
            <th mat-header-cell *matHeaderCellDef>Stripe PI ID</th>
            <td mat-cell *matCellDef="let i">{{ i.stripePaymentIntentId || '—' }}</td></ng-container>
          <ng-container matColumnDef="localId">
            <th mat-header-cell *matHeaderCellDef>Local ID</th>
            <td mat-cell *matCellDef="let i">{{ i.localPaymentId || '—' }}</td></ng-container>
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let i">
              <mat-chip [class]="'type-' + i.discrepancyType.toLowerCase()">{{ i.discrepancyType }}</mat-chip>
            </td></ng-container>
          <ng-container matColumnDef="stripeAmount">
            <th mat-header-cell *matHeaderCellDef>Stripe Amt</th>
            <td mat-cell *matCellDef="let i">{{ i.stripeAmount ?? '—' }}</td></ng-container>
          <ng-container matColumnDef="localAmount">
            <th mat-header-cell *matHeaderCellDef>Local Amt</th>
            <td mat-cell *matCellDef="let i">{{ i.localAmount ?? '—' }}</td></ng-container>
          <ng-container matColumnDef="stripeStatus">
            <th mat-header-cell *matHeaderCellDef>Stripe Status</th>
            <td mat-cell *matCellDef="let i">{{ i.stripeStatus || '—' }}</td></ng-container>
          <ng-container matColumnDef="localStatus">
            <th mat-header-cell *matHeaderCellDef>Local Status</th>
            <td mat-cell *matCellDef="let i">{{ i.localStatus || '—' }}</td></ng-container>
          <ng-container matColumnDef="resolved">
            <th mat-header-cell *matHeaderCellDef>Resolved</th>
            <td mat-cell *matCellDef="let i">{{ i.resolved ? 'Yes' : 'No' }}</td></ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let i">
              @if (!i.resolved && i.discrepancyType !== 'MATCHED') {
                <button mat-flat-button color="primary" (click)="resolve(i)">Resolve</button>
              }
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
        <mat-paginator [length]="totalItems()" [pageSize]="20" (page)="onPage($event)"></mat-paginator>
      }

    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .stats-bar { display: flex; gap: 16px; align-items: center; margin: 16px 0; flex-wrap: wrap; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
    .export-btn { margin: 16px 0; }
    .type-matched { background: #4caf50 !important; color: white; }
    .type-status_mismatch, .type-amount_mismatch { background: #ff9800 !important; color: white; }
    .type-missing_local, .type-missing_stripe { background: #f44336 !important; color: white; }
    mat-button-toggle-group { margin: 16px 0; }
  `]
})
export class ReconciliationDetailComponent implements OnInit {
  private service = inject(ReconciliationService);
  private route = inject(ActivatedRoute);
  private snackBar = inject(MatSnackBar);

  run = signal<ReconciliationRun | null>(null);
  items = signal<ReconciliationItem[]>([]);
  totalItems = signal(0);
  loading = signal(true);
  activeFilter = signal<string>('ALL');
  runId = 0;
  columns = ['stripeId', 'localId', 'type', 'stripeAmount', 'localAmount', 'stripeStatus', 'localStatus', 'resolved', 'actions'];

  ngOnInit(): void {
    this.runId = +this.route.snapshot.paramMap.get('runId')!;
    this.service.getRunDetails(this.runId).subscribe(r => this.run.set(r));
    this.loadItems(0);
  }

  loadItems(page: number, type?: DiscrepancyType): void {
    this.loading.set(true);
    this.service.getRunItems(this.runId, page, 20, type).subscribe({
      next: p => { this.items.set(p.content); this.totalItems.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  filterByType(value: string): void {
    this.activeFilter.set(value);
    const type = value === 'ALL' ? undefined : value as DiscrepancyType;
    this.loadItems(0, type);
  }

  resolve(item: ReconciliationItem): void {
    const notes = window.prompt('Resolution notes (optional):');
    if (notes === null) return; // cancelled
    this.service.resolveItem(item.id, notes).subscribe({
      next: () => {
        this.snackBar.open('Item resolved', 'OK', { duration: 3000 });
        this.loadItems(0, this.activeFilter() === 'ALL' ? undefined : this.activeFilter() as DiscrepancyType);
      },
      error: () => this.snackBar.open('Failed to resolve', 'OK', { duration: 3000 })
    });
  }

  exportCsv(): void {
    this.service.getRunItems(this.runId, 0, 10000).subscribe(page => {
      const headers = 'Stripe PI ID,Local ID,Type,Stripe Amount,Local Amount,Stripe Status,Local Status,Resolved,Notes\n';
      const rows = page.content.map(i =>
        `${i.stripePaymentIntentId ?? ''},${i.localPaymentId ?? ''},${i.discrepancyType},${i.stripeAmount ?? ''},${i.localAmount ?? ''},${i.stripeStatus ?? ''},${i.localStatus ?? ''},${i.resolved},${(i.notes || '').replace(/,/g, ';')}`
      ).join('\n');
      const blob = new Blob([headers + rows], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `reconciliation-run-${this.runId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  onPage(event: PageEvent): void {
    const type = this.activeFilter() === 'ALL' ? undefined : this.activeFilter() as DiscrepancyType;
    this.loadItems(event.pageIndex, type);
  }
}
