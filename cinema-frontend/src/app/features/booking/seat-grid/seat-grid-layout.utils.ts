import { Seat } from '../../../core/models/movie.model';

/** Row data: letter label + sorted seats for that row */
export interface SeatRow {
  label: string;
  rowNumber: number;
  seatType: string; // dominant seat type for this row
  seats: Seat[];
}

/** Section groups consecutive rows of the same seat type */
export interface SeatSection {
  type: string;
  rows: SeatRow[];
}

/** Legend entry: seat type + representative price */
export interface LegendType {
  type: string;
  price: number;
  cssClass: string;
}

const SEAT_TYPE_ORDER = ['STANDARD', 'PREMIUM', 'VIP'];

/** Compute aisle column positions (0-indexed). Splits seats into 3 sections. */
export function getAislePositions(totalColumns: number): Set<number> {
  if (totalColumns < 6) return new Set(); // too few columns for aisles
  const firstAisle = Math.floor(totalColumns / 3);
  const secondAisle = Math.floor(2 * totalColumns / 3);
  return new Set([firstAisle, secondAisle]);
}

/** Check if there should be an aisle gap before this seat's column (1-indexed columnNumber) */
export function isAisleBefore(columnNumber: number, aislePositions: Set<number>): boolean {
  return aislePositions.has(columnNumber - 1); // columnNumber is 1-indexed, aisles are 0-indexed boundary
}

/** Group seats into rows sorted by rowNumber */
export function buildSeatRows(seats: Seat[]): SeatRow[] {
  const rowMap = new Map<number, Seat[]>();
  for (const seat of seats) {
    const arr = rowMap.get(seat.rowNumber) || [];
    arr.push(seat);
    rowMap.set(seat.rowNumber, arr);
  }
  return Array.from(rowMap.entries())
    .sort(([a], [b]) => a - b)
    .map(([rowNum, rowSeats]) => {
      const sorted = rowSeats.sort((a, b) => a.columnNumber - b.columnNumber);
      // Dominant seat type = most common type in row
      const typeCounts = new Map<string, number>();
      for (const s of sorted) {
        typeCounts.set(s.seatType, (typeCounts.get(s.seatType) || 0) + 1);
      }
      const dominant = Array.from(typeCounts.entries())
        .sort((a, b) => b[1] - a[1])[0]?.[0] || 'STANDARD';
      return {
        label: sorted[0]?.seatLabel?.charAt(0) || String.fromCharCode(64 + rowNum),
        rowNumber: rowNum,
        seatType: dominant,
        seats: sorted
      };
    });
}

/** Group consecutive rows by seat type into sections */
export function buildSeatSections(rows: SeatRow[]): SeatSection[] {
  if (rows.length === 0) return [];
  const sections: SeatSection[] = [];
  let current: SeatSection = { type: rows[0].seatType, rows: [rows[0]] };
  for (let i = 1; i < rows.length; i++) {
    if (rows[i].seatType === current.type) {
      current.rows.push(rows[i]);
    } else {
      sections.push(current);
      current = { type: rows[i].seatType, rows: [rows[i]] };
    }
  }
  sections.push(current);
  return sections;
}

/** Build legend entries sorted by type order */
export function buildLegendTypes(seats: Seat[]): LegendType[] {
  const seen = new Map<string, number>();
  for (const seat of seats) {
    if (!seen.has(seat.seatType)) {
      seen.set(seat.seatType, seat.price);
    }
  }
  return Array.from(seen.entries())
    .sort(([a], [b]) => SEAT_TYPE_ORDER.indexOf(a) - SEAT_TYPE_ORDER.indexOf(b))
    .map(([type, price]) => ({ type, price, cssClass: type.toLowerCase() }));
}
