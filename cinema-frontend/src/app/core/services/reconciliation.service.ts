import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ReconciliationRun,
  ReconciliationItem,
  ReconciliationSummary,
  DiscrepancyType,
  PageResponse
} from '../models/reconciliation.model';

@Injectable({ providedIn: 'root' })
export class ReconciliationService {
  private http = inject(HttpClient);
  private base = '/api/payments/reconciliation';

  triggerReconciliation(startDate: string, endDate: string): Observable<ReconciliationRun> {
    return this.http.post<ReconciliationRun>(`${this.base}/trigger`, { startDate, endDate });
  }

  getRuns(page = 0, size = 10): Observable<PageResponse<ReconciliationRun>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ReconciliationRun>>(`${this.base}/runs`, { params });
  }

  getRunDetails(runId: number): Observable<ReconciliationRun> {
    return this.http.get<ReconciliationRun>(`${this.base}/runs/${runId}`);
  }

  getRunItems(runId: number, page = 0, size = 20, discrepancyType?: DiscrepancyType): Observable<PageResponse<ReconciliationItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (discrepancyType) params = params.set('discrepancyType', discrepancyType);
    return this.http.get<PageResponse<ReconciliationItem>>(`${this.base}/runs/${runId}/items`, { params });
  }

  getSummary(): Observable<ReconciliationSummary> {
    return this.http.get<ReconciliationSummary>(`${this.base}/summary`);
  }

  resolveItem(itemId: number, notes: string): Observable<ReconciliationItem> {
    return this.http.put<ReconciliationItem>(`${this.base}/items/${itemId}/resolve`, { notes });
  }
}
