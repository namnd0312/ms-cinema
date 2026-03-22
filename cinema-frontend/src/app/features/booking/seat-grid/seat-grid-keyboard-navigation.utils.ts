import { Seat } from '../../../core/models/movie.model';
import { SeatRow } from './seat-grid-layout.utils';

/** Handles arrow key navigation within the seat grid (roving tabindex pattern) */
export interface FocusPosition {
  rowIndex: number;
  colIndex: number;
}

/**
 * Compute next focus position based on keyboard event.
 * Returns null if the event is not a navigation key.
 */
export function computeNextFocus(
  event: KeyboardEvent,
  current: FocusPosition,
  rows: SeatRow[]
): FocusPosition | null {
  const { rowIndex, colIndex } = current;
  const totalRows = rows.length;
  if (totalRows === 0) return null;

  switch (event.key) {
    case 'ArrowRight': {
      const maxCol = rows[rowIndex].seats.length - 1;
      return { rowIndex, colIndex: Math.min(colIndex + 1, maxCol) };
    }
    case 'ArrowLeft':
      return { rowIndex, colIndex: Math.max(colIndex - 1, 0) };
    case 'ArrowDown': {
      const nextRow = Math.min(rowIndex + 1, totalRows - 1);
      const maxCol = rows[nextRow].seats.length - 1;
      return { rowIndex: nextRow, colIndex: Math.min(colIndex, maxCol) };
    }
    case 'ArrowUp': {
      const prevRow = Math.max(rowIndex - 1, 0);
      const maxCol = rows[prevRow].seats.length - 1;
      return { rowIndex: prevRow, colIndex: Math.min(colIndex, maxCol) };
    }
    case 'Home':
      return { rowIndex, colIndex: 0 };
    case 'End':
      return { rowIndex, colIndex: rows[rowIndex].seats.length - 1 };
    default:
      return null;
  }
}

/** Get the seat at a given focus position, or null if out of bounds */
export function getSeatAtPosition(rows: SeatRow[], pos: FocusPosition): Seat | null {
  const row = rows[pos.rowIndex];
  if (!row) return null;
  return row.seats[pos.colIndex] ?? null;
}

/** Build ARIA label for a seat button */
export function buildSeatAriaLabel(seat: Seat, state: string): string {
  const stateLabel = state === 'selected' ? 'Selected' : state === 'occupied' ? 'Occupied' : 'Available';
  return `Seat ${seat.seatLabel}, ${seat.seatType}, $${seat.price}, ${stateLabel}`;
}
