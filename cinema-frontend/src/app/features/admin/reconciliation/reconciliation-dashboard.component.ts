import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ReconciliationService } from '../../../core/services/reconciliation.service';
import { formatDate } from '../../../core/utils/date-format.util';
import { ReconciliationRun, ReconciliationSummary } from '../../../core/models/reconciliation.model';

@Component({
  selector: 'app-reconciliation-dashboard',
  standalone: true,
  imports: [
    DatePipe, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatChipsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatPaginatorModule,
    MatDatepickerModule
  ],
  template: `
    <div class="admin-container">
      <h1>Payment Reconciliation</h1>

      <!-- Summary Cards -->
      @if (summary()) {
        <div class="summary-cards">
          <mat-card><mat-card-header><mat-card-title>Matched</mat-card-title></mat-card-header>
            <mat-card-content><span class="count matched">{{ summary()!.matched }}</span></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-title>Mismatched</mat-card-title></mat-card-header>
            <mat-card-content><span class="count mismatched">{{ summary()!.mismatched }}</span></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-title>Missing Local</mat-card-title></mat-card-header>
            <mat-card-content><span class="count missing">{{ summary()!.missingLocal }}</span></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-title>Missing Stripe</mat-card-title></mat-card-header>
            <mat-card-content><span class="count missing">{{ summary()!.missingStripe }}</span></mat-card-content></mat-card>
        </div>
      }

      <!-- Trigger Section -->
      <div class="trigger-section">
        <mat-form-field><mat-label>Start Date</mat-label>
          <input matInput [matDatepicker]="startDp" [(ngModel)]="startDate">
          <mat-datepicker-toggle matSuffix [for]="startDp"></mat-datepicker-toggle>
          <mat-datepicker #startDp></mat-datepicker></mat-form-field>
        <mat-form-field><mat-label>End Date</mat-label>
          <input matInput [matDatepicker]="endDp" [(ngModel)]="endDate">
          <mat-datepicker-toggle matSuffix [for]="endDp"></mat-datepicker-toggle>
          <mat-datepicker #endDp></mat-datepicker></mat-form-field>
        <button mat-raised-button color="primary" (click)="trigger()" [disabled]="triggering()">
          @if (triggering()) { <mat-spinner diameter="20"></mat-spinner> }
          @else { Run Reconciliation }
        </button>
      </div>

      <!-- Run History Table -->
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        <table mat-table [dataSource]="runs()" class="full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let r">{{ r.id }}</td></ng-container>
          <ng-container matColumnDef="dateRange">
            <th mat-header-cell *matHeaderCellDef>Date Range</th>
            <td mat-cell *matCellDef="let r">{{ r.startDate }} — {{ r.endDate }}</td></ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let r">
              <mat-chip [class]="'status-' + r.status.toLowerCase()">{{ r.status }}</mat-chip>
            </td></ng-container>
          <ng-container matColumnDef="matched">
            <th mat-header-cell *matHeaderCellDef>Matched</th>
            <td mat-cell *matCellDef="let r">{{ r.matchedCount }}</td></ng-container>
          <ng-container matColumnDef="mismatched">
            <th mat-header-cell *matHeaderCellDef>Mismatched</th>
            <td mat-cell *matCellDef="let r">{{ r.mismatchedCount }}</td></ng-container>
          <ng-container matColumnDef="missing">
            <th mat-header-cell *matHeaderCellDef>Missing</th>
            <td mat-cell *matCellDef="let r">{{ r.missingLocalCount + r.missingStripeCount }}</td></ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Created</th>
            <td mat-cell *matCellDef="let r">{{ r.createdAt | date:'medium' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;" class="clickable" (click)="viewRun(row.id)"></tr>
        </table>
        <mat-paginator [length]="totalRuns()" [pageSize]="10" (page)="onPage($event)"></mat-paginator>
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .summary-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
    .count { font-size: 32px; font-weight: bold; display: block; text-align: center; padding: 8px; }
    .matched { color: #4caf50; } .mismatched { color: #ff9800; } .missing { color: #f44336; }
    .trigger-section { display: flex; gap: 16px; align-items: center; margin-bottom: 24px; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
    .clickable { cursor: pointer; } .clickable:hover { background: rgba(0,0,0,0.04); }
    .status-completed { background: #4caf50 !important; color: white; }
    .status-running { background: #ff9800 !important; color: white; }
    .status-failed { background: #f44336 !important; color: white; }
  `]
})
export class ReconciliationDashboardComponent implements OnInit {
  private service = inject(ReconciliationService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  summary = signal<ReconciliationSummary | null>(null);
  runs = signal<ReconciliationRun[]>([]);
  totalRuns = signal(0);
  loading = signal(true);
  triggering = signal(false);
  startDate: Date | null = null;
  endDate: Date | null = null;
  columns = ['id', 'dateRange', 'status', 'matched', 'mismatched', 'missing', 'createdAt'];

  ngOnInit(): void {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    this.startDate = this.endDate = yesterday;
    this.loadSummary();
    this.loadRuns(0);
  }

  loadSummary(): void {
    this.service.getSummary().subscribe({ next: s => this.summary.set(s), error: () => {} });
  }

  loadRuns(page: number): void {
    this.loading.set(true);
    this.service.getRuns(page).subscribe({
      next: p => { this.runs.set(p.content); this.totalRuns.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  trigger(): void {
    if (!this.startDate || !this.endDate) return;
    this.triggering.set(true);
    this.service.triggerReconciliation(formatDate(this.startDate), formatDate(this.endDate)).subscribe({
      next: () => {
        this.snackBar.open('Reconciliation started', 'OK', { duration: 3000 });
        this.triggering.set(false);
        this.loadRuns(0);
        setTimeout(() => { this.loadSummary(); this.loadRuns(0); }, 5000);
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to trigger', 'OK', { duration: 3000 });
        this.triggering.set(false);
      }
    });
  }

  viewRun(id: number): void { this.router.navigate(['admin', 'reconciliation', id]); }
  onPage(event: PageEvent): void { this.loadRuns(event.pageIndex); }
}
