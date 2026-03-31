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
